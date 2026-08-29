package com.example.fittrack.data.community

import android.net.Uri
import com.example.fittrack.data.media.PostImageStore
import com.example.fittrack.data.prefs.UserPreferences
import com.example.fittrack.data.remote.CommunityFirestoreSource as Src
import com.example.fittrack.data.remote.PostImageApi
import com.example.fittrack.domain.model.AuthUser
import com.example.fittrack.domain.model.Community
import com.example.fittrack.domain.model.CommunityLimits
import com.example.fittrack.domain.model.CommunityMember
import com.example.fittrack.domain.model.CommunityMetric
import com.example.fittrack.domain.model.CommunityPost
import com.example.fittrack.domain.model.JoinRequest
import com.example.fittrack.domain.model.LeaderboardEntry
import com.example.fittrack.domain.model.PostComment
import com.example.fittrack.domain.model.Reaction
import com.example.fittrack.domain.model.WeeklyWinner
import com.example.fittrack.domain.repository.AuthRepository
import com.example.fittrack.domain.repository.CommunityRepository
import com.example.fittrack.domain.repository.Leaderboard
import com.example.fittrack.domain.repository.StepRepository
import com.example.fittrack.domain.repository.WorkoutRepository
import com.example.fittrack.domain.util.CommunityWeek
import com.example.fittrack.domain.util.WeeklyScoreCalculator
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The community feature's whole data layer.
 *
 * Every method returns [Result]. None of this works offline, so a failure is an
 * ordinary outcome the screen has to render, and the messages are written for
 * the person reading them rather than for a log.
 */
@Singleton
class CommunityRepositoryImpl @Inject constructor(
    private val source: Src,
    private val postImageApi: PostImageApi,
    private val postImageStore: PostImageStore,
    private val authRepository: AuthRepository,
    private val userPreferences: UserPreferences,
    private val stepRepository: StepRepository,
    private val workoutRepository: WorkoutRepository
) : CommunityRepository {

    // ------------------------------------------------------------ discovery

    override suspend fun myCommunities(): Result<List<Community>> = call { user ->
        source.myCommunities(user.uid).documents
            .mapNotNull { it.toCommunity(user.uid) }
            .sortedBy { it.name.lowercase() }
    }

    override suspend fun discover(query: String): Result<List<Community>> = call { user ->
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return@call source.popular().documents.mapNotNull { it.toCommunity(user.uid) }
        }

        // Searched by id and by name at once, because people are given a code
        // to type and also expect to find a group they half-remember the name
        // of. A code is short and upper-case; a name is not, so an id lookup on
        // a long phrase would only ever be a wasted read.
        val byId = if (trimmed.length <= ID_SEARCH_MAX) {
            runCatching { source.byId(trimmed.uppercase()) }.getOrNull()
                ?.takeIf { it.exists() }
                ?.toCommunity(user.uid)
        } else {
            null
        }

        val byName = source.searchByName(trimmed.lowercase()).documents
            .mapNotNull { it.toCommunity(user.uid) }

        // The exact id match goes first: someone typing a code is looking for
        // one specific group, not browsing.
        (listOfNotNull(byId) + byName).distinctBy { it.id }
    }

    override suspend fun community(communityId: String): Result<Community> = call { user ->
        source.byId(communityId).toCommunity(user.uid)
            ?: throw IllegalStateException("That community no longer exists.")
    }

    // ------------------------------------------------------------ lifecycle

    override suspend fun create(
        name: String,
        description: String,
        icon: String,
        metric: CommunityMetric
    ): Result<Community> = call { user ->
        val cleanName = name.trim()
        require(cleanName.length >= CommunityLimits.NAME_MIN) {
            "Give the community a name of at least ${CommunityLimits.NAME_MIN} characters."
        }
        require(cleanName.length <= CommunityLimits.NAME_MAX) {
            "That name is too long."
        }

        val mine = source.myCommunities(user.uid).size()
        require(mine < CommunityLimits.MAX_COMMUNITIES_PER_USER) {
            "You are already in ${CommunityLimits.MAX_COMMUNITIES_PER_USER} communities."
        }

        val displayName = user.communityName()
        val now = System.currentTimeMillis()
        val id = source.createWithUniqueId(
            mapOf(
                Src.FIELD_NAME to cleanName,
                // Firestore cannot search case-insensitively, so the lowercased
                // copy is what the prefix query actually runs against.
                Src.FIELD_NAME_LOWER to cleanName.lowercase(),
                Src.FIELD_DESCRIPTION to description.trim().take(CommunityLimits.DESCRIPTION_MAX),
                Src.FIELD_ICON to icon,
                Src.FIELD_ADMIN_UID to user.uid,
                Src.FIELD_ADMIN_NAME to displayName,
                Src.FIELD_METRIC to metric.storageName,
                Src.FIELD_MEMBER_UIDS to listOf(user.uid),
                Src.FIELD_PENDING_UIDS to emptyList<String>(),
                Src.FIELD_BANNED_UIDS to emptyList<String>(),
                Src.FIELD_MEMBER_COUNT to 1,
                Src.FIELD_CREATED_AT to now
            )
        )

        // The creator is a member like anyone else and needs a roster row, or
        // they would be the one person missing from their own leaderboard.
        source.members(id).document(user.uid).set(
            mapOf(
                Src.FIELD_UID to user.uid,
                Src.FIELD_NAME to displayName,
                Src.FIELD_AVATAR_URL to myAvatarUrl(),
                Src.FIELD_JOINED_AT to now
            )
        ).await()

        Community(
            id = id,
            name = cleanName,
            description = description.trim(),
            icon = icon,
            adminUid = user.uid,
            adminName = displayName,
            metric = metric,
            memberCount = 1,
            createdAt = now,
            isMember = true,
            isAdmin = true
        )
    }

    override suspend fun requestToJoin(communityId: String): Result<Unit> = call { user ->
        val community = source.byId(communityId).toCommunity(user.uid)
            ?: throw IllegalStateException("That community no longer exists.")
        if (community.isMember || community.hasPendingRequest) return@call
        if (community.isBanned) {
            throw IllegalStateException("You can't rejoin this community.")
        }
        if (community.memberCount >= CommunityLimits.MAX_MEMBERS) {
            throw IllegalStateException("This community is full.")
        }
        source.addJoinRequest(communityId, user.uid, user.communityName(), myAvatarUrl())
    }

    override suspend fun withdrawRequest(communityId: String): Result<Unit> = call { user ->
        source.removeJoinRequest(communityId, user.uid)
    }

    override suspend fun leave(communityId: String): Result<Unit> = call { user ->
        val community = source.byId(communityId).toCommunity(user.uid)
            ?: throw IllegalStateException("That community no longer exists.")
        if (community.isAdmin) {
            throw IllegalStateException(
                "You run this community. Hand it to another member or delete it first."
            )
        }
        source.leave(communityId, user.uid)
        // Their posts stay, but a leaderboard entry for someone who is not in
        // the group would sit there for the rest of the week.
        runCatching {
            source.scores(communityId)
                .document("${CommunityWeek.idFor()}_${user.uid}")
                .delete()
                .await()
        }
    }

    override suspend fun deleteCommunity(communityId: String): Result<Unit> = call { user ->
        val community = source.byId(communityId).toCommunity(user.uid)
            ?: return@call
        if (!community.isAdmin) throw IllegalStateException("Only the admin can delete this.")

        // Photos live on the VPS and Firestore knows nothing about them, so
        // they have to come down before the posts that point at them do --
        // afterwards there is no record of which files were ever involved.
        runCatching {
            val token = authRepository.currentIdToken()
            if (token != null) {
                source.posts(communityId).get().await().documents.forEach { doc ->
                    doc.getString(Src.FIELD_IMAGE_ID)?.let { imageId ->
                        deleteImage(token, imageId, communityId)
                    }
                }
            }
        }
        source.deleteCommunity(communityId)
    }

    // ---------------------------------------------------------------- admin

    override suspend fun pendingRequests(communityId: String): Result<List<JoinRequest>> = call {
        source.requests(communityId).get().await().documents.map { doc ->
            JoinRequest(
                uid = doc.getString(Src.FIELD_UID).orEmpty(),
                name = doc.getString(Src.FIELD_NAME) ?: "Member",
                avatarUrl = doc.getString(Src.FIELD_AVATAR_URL),
                requestedAt = doc.getLong(Src.FIELD_REQUESTED_AT) ?: 0L
            )
        }.sortedBy { it.requestedAt }
    }

    override suspend fun approve(communityId: String, uid: String): Result<Unit> = call {
        val request = source.requests(communityId).document(uid).get().await()
        if (!request.exists()) throw IllegalStateException("That request was withdrawn.")
        source.approve(communityId, request)
    }

    override suspend fun reject(communityId: String, uid: String): Result<Unit> = call {
        source.removeJoinRequest(communityId, uid)
    }

    override suspend fun members(communityId: String): Result<List<CommunityMember>> = call {
        val adminUid = source.byId(communityId).getString(Src.FIELD_ADMIN_UID)
        source.members(communityId).get().await().documents
            .map { it.toMember(adminUid) }
            // Admin first, then longest-standing: the order people expect a
            // roster in, and the one that makes the admin obvious.
            .sortedWith(compareByDescending<CommunityMember> { it.isAdmin }.thenBy { it.joinedAt })
    }

    override suspend fun removeMember(communityId: String, uid: String): Result<Unit> = call { user ->
        if (uid == user.uid) throw IllegalStateException("You can't remove yourself.")
        source.removeAndBan(communityId, uid)
        runCatching {
            source.scores(communityId).document("${CommunityWeek.idFor()}_$uid").delete().await()
        }
    }

    override suspend fun unban(communityId: String, uid: String): Result<Unit> = call {
        source.unban(communityId, uid)
    }

    override suspend fun bannedMembers(communityId: String): Result<List<CommunityMember>> = call {
        // Only uids are kept for banned people: their roster row is deleted
        // when they are removed, and holding a name and photo for someone who
        // is no longer in the group is more than the feature needs.
        val snapshot = source.byId(communityId)
        (snapshot.get(Src.FIELD_BANNED_UIDS) as? List<*>)
            ?.filterIsInstance<String>()
            ?.map { CommunityMember(it, "Removed member", null, 0L, false) }
            ?: emptyList()
    }

    override suspend fun transferAdmin(communityId: String, toUid: String): Result<Unit> = call {
        val member = source.members(communityId).document(toUid).get().await()
        if (!member.exists()) throw IllegalStateException("That person is not a member.")
        source.transferAdmin(communityId, toUid, member.getString(Src.FIELD_NAME) ?: "Member")
    }

    override suspend fun setMetric(communityId: String, metric: CommunityMetric): Result<Unit> =
        call {
            source.setMetric(communityId, metric.storageName)
            // Everyone's published number is in the old unit now. Only this
            // device can fix its own, and the rest correct themselves as their
            // owners open the app -- which is why the board shows how stale
            // each row is.
            runCatching { publishScoreFor(communityId, metric) }
        }

    // ---------------------------------------------------------- leaderboard

    override suspend fun leaderboard(communityId: String): Result<Leaderboard> = call { user ->
        val communityDoc = source.byId(communityId)
        val metric = CommunityMetric.fromStorage(communityDoc.getString(Src.FIELD_METRIC))
        val adminUid = communityDoc.getString(Src.FIELD_ADMIN_UID)

        val members = source.members(communityId).get().await().documents.map { it.toMember(adminUid) }
        val scores = source.weekScores(communityId, CommunityWeek.idFor()).documents
            .associateBy { it.getString(Src.FIELD_UID) }

        val ranked = members
            .map { member ->
                val score = scores[member.uid]
                Triple(member, score?.getLong(Src.FIELD_VALUE)?.toInt() ?: 0, score)
            }
            // Zero-activity members stay on the board rather than dropping off
            // it. Being visibly last is most of the point of a leaderboard.
            .sortedWith(compareByDescending<Triple<CommunityMember, Int, DocumentSnapshot?>> { it.second }
                .thenBy { it.first.name.lowercase() })

        var lastValue = Int.MIN_VALUE
        var lastRank = 0
        val entries = ranked.mapIndexed { index, (member, value, score) ->
            // Equal scores share a rank; the next one down still counts every
            // row above it, so two people tied at 1st are followed by 3rd.
            val rank = if (value == lastValue) lastRank else index + 1
            lastValue = value
            lastRank = rank
            LeaderboardEntry(
                uid = member.uid,
                name = member.name,
                avatarUrl = member.avatarUrl,
                value = value,
                updatedAt = score?.getLong(Src.FIELD_UPDATED_AT) ?: 0L,
                rank = rank,
                isMe = member.uid == user.uid
            )
        }

        Leaderboard(
            metric = metric,
            weekLabel = CommunityWeek.label(),
            entries = entries,
            lastWeekWinner = lastWeekWinner(communityId, metric)
        )
    }

    private suspend fun lastWeekWinner(communityId: String, metric: CommunityMetric): WeeklyWinner? =
        runCatching {
            source.weekScores(communityId, CommunityWeek.previousId()).documents
                .maxByOrNull { it.getLong(Src.FIELD_VALUE) ?: 0L }
                ?.takeIf { (it.getLong(Src.FIELD_VALUE) ?: 0L) > 0L }
                ?.let {
                    WeeklyWinner(
                        name = it.getString(Src.FIELD_NAME) ?: "Member",
                        avatarUrl = it.getString(Src.FIELD_AVATAR_URL),
                        value = (it.getLong(Src.FIELD_VALUE) ?: 0L).toInt(),
                        metric = metric
                    )
                }
        }.getOrNull()

    override suspend fun publishMyScore(communityId: String): Result<Unit> = call {
        val metric = CommunityMetric.fromStorage(
            source.byId(communityId).getString(Src.FIELD_METRIC)
        )
        publishScoreFor(communityId, metric)
    }

    private suspend fun publishScoreFor(communityId: String, metric: CommunityMetric) {
        val user = authRepository.currentUser ?: return
        val value = WeeklyScoreCalculator.scoreFor(
            metric = metric,
            steps = stepRepository.getAllSteps().first(),
            workouts = workoutRepository.getAllWorkouts().first()
        )
        source.publishScore(
            cid = communityId,
            weekId = CommunityWeek.idFor(),
            uid = user.uid,
            name = user.communityName(),
            avatarUrl = myAvatarUrl(),
            value = value
        )
    }

    // ----------------------------------------------------------------- feed

    override suspend fun posts(
        communityId: String,
        beforeCreatedAt: Long?
    ): Result<List<CommunityPost>> = call { user ->
        val page = source.postPage(communityId, beforeCreatedAt)
        val adminUid = source.byId(communityId).getString(Src.FIELD_ADMIN_UID)

        // One query for every reaction this user has left in the community,
        // rather than one read per post. On a twenty-post page that is the
        // difference between twenty-one reads and two.
        val mine = runCatching {
            source.myReactions(communityId, user.uid).documents
                .associate { it.getString(Src.FIELD_POST_ID) to it.getString(Src.FIELD_EMOJI) }
        }.getOrDefault(emptyMap())

        page.documents.map { doc ->
            doc.toPost(
                myReaction = Reaction.fromStorage(mine[doc.id]),
                canDelete = doc.getString(Src.FIELD_AUTHOR_UID) == user.uid || adminUid == user.uid
            )
        }
    }

    override suspend fun createPost(
        communityId: String,
        text: String,
        image: Uri?
    ): Result<Unit> = call { user ->
        val body = text.trim()
        require(body.isNotEmpty()) { "Write something to post." }
        require(body.length <= CommunityLimits.POST_TEXT_MAX) {
            "Keep a post under ${CommunityLimits.POST_TEXT_MAX} characters."
        }

        var imageId: String? = null
        var imageUrl: String? = null
        var prepared: File? = null

        if (image != null) {
            val token = authRepository.currentIdToken()
                ?: throw IllegalStateException("Sign in again to attach a photo.")
            prepared = postImageStore.prepare(image)
                ?: throw IllegalStateException("That image couldn't be read.")
            val uploaded = uploadImage(prepared, token)
            imageId = uploaded.first
            imageUrl = uploaded.second
        }

        try {
            source.createPost(
                communityId,
                mapOf(
                    Src.FIELD_AUTHOR_UID to user.uid,
                    Src.FIELD_AUTHOR_NAME to user.communityName(),
                    Src.FIELD_AVATAR_URL to myAvatarUrl(),
                    Src.FIELD_TEXT to body,
                    Src.FIELD_IMAGE_URL to imageUrl,
                    Src.FIELD_IMAGE_ID to imageId,
                    Src.FIELD_CREATED_AT to System.currentTimeMillis(),
                    Src.FIELD_REACTION_COUNTS to emptyMap<String, Int>(),
                    Src.FIELD_COMMENT_COUNT to 0
                )
            )
        } catch (e: Exception) {
            // The photo is already on the server but nothing points at it any
            // more, and nothing ever will. Take it back down rather than leave
            // a file nobody can see and nobody can delete.
            imageId?.let { orphan ->
                val token = authRepository.currentIdToken()
                if (token != null) runCatching { deleteImage(token, orphan, communityId) }
            }
            throw e
        } finally {
            postImageStore.discard(prepared)
        }
    }

    override suspend fun deletePost(communityId: String, post: CommunityPost): Result<Unit> =
        call {
            source.deletePostAndChildren(communityId, post.id)
            // After the document is gone there is no record of the file, so a
            // failure here would strand it for good -- but the post is already
            // deleted as far as the user is concerned, so it must not surface
            // as an error either.
            post.imageId?.let { imageId ->
                val token = authRepository.currentIdToken()
                if (token != null) runCatching { deleteImage(token, imageId, communityId) }
            }
        }

    override suspend fun react(
        communityId: String,
        postId: String,
        reaction: Reaction?
    ): Result<Unit> = call { user ->
        val previous = source.currentReaction(communityId, postId, user.uid)
        source.setReaction(
            cid = communityId,
            postId = postId,
            uid = user.uid,
            previous = previous,
            // Tapping the reaction already showing clears it, which is what
            // makes a single row of emoji behave like a toggle.
            next = reaction?.storageName?.takeIf { it != previous }
        )
    }

    // ------------------------------------------------------------- comments

    override suspend fun comments(
        communityId: String,
        postId: String,
        beforeCreatedAt: Long?
    ): Result<List<PostComment>> = call { user ->
        val adminUid = source.byId(communityId).getString(Src.FIELD_ADMIN_UID)
        source.commentPage(communityId, postId, beforeCreatedAt).documents.map { doc ->
            PostComment(
                id = doc.id,
                authorUid = doc.getString(Src.FIELD_AUTHOR_UID).orEmpty(),
                authorName = doc.getString(Src.FIELD_AUTHOR_NAME) ?: "Member",
                authorAvatarUrl = doc.getString(Src.FIELD_AVATAR_URL),
                text = doc.getString(Src.FIELD_TEXT).orEmpty(),
                createdAt = doc.getLong(Src.FIELD_CREATED_AT) ?: 0L,
                canDelete = doc.getString(Src.FIELD_AUTHOR_UID) == user.uid || adminUid == user.uid
            )
        }
    }

    override suspend fun addComment(
        communityId: String,
        postId: String,
        text: String
    ): Result<Unit> = call { user ->
        val body = text.trim()
        require(body.isNotEmpty()) { "Write something first." }
        require(body.length <= CommunityLimits.COMMENT_TEXT_MAX) {
            "Keep a comment under ${CommunityLimits.COMMENT_TEXT_MAX} characters."
        }
        source.addComment(
            communityId,
            postId,
            mapOf(
                Src.FIELD_AUTHOR_UID to user.uid,
                Src.FIELD_AUTHOR_NAME to user.communityName(),
                Src.FIELD_AVATAR_URL to myAvatarUrl(),
                Src.FIELD_TEXT to body,
                Src.FIELD_CREATED_AT to System.currentTimeMillis()
            )
        )
    }

    override suspend fun deleteComment(
        communityId: String,
        postId: String,
        commentId: String
    ): Result<Unit> = call {
        source.deleteComment(communityId, postId, commentId)
    }

    // -------------------------------------------------------------- images

    private suspend fun uploadImage(file: File, token: String): Pair<String?, String?> {
        val body = file.asRequestBody(JPEG.toMediaType())
        val part = MultipartBody.Part.createFormData("image", "post.jpg", body)
        val response = postImageApi.upload("Bearer $token", part)
        val url = response.url?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException(response.error ?: "The photo could not be uploaded.")
        return response.imageId to url
    }

    private suspend fun deleteImage(token: String, imageId: String, communityId: String) {
        postImageApi.delete("Bearer $token", imageId, communityId)
    }

    // ------------------------------------------------------------- mapping

    private fun DocumentSnapshot.toCommunity(uid: String): Community? {
        if (!exists()) return null
        val name = getString(Src.FIELD_NAME) ?: return null
        val memberUids = uidList(Src.FIELD_MEMBER_UIDS)
        return Community(
            id = id,
            name = name,
            description = getString(Src.FIELD_DESCRIPTION).orEmpty(),
            icon = getString(Src.FIELD_ICON) ?: DEFAULT_ICON,
            adminUid = getString(Src.FIELD_ADMIN_UID).orEmpty(),
            adminName = getString(Src.FIELD_ADMIN_NAME) ?: "Admin",
            metric = CommunityMetric.fromStorage(getString(Src.FIELD_METRIC)),
            // Falls back to the array's own length: a stored count is only ever
            // an index for sorting, never the truth about who is in the group.
            memberCount = getLong(Src.FIELD_MEMBER_COUNT)?.toInt() ?: memberUids.size,
            createdAt = getLong(Src.FIELD_CREATED_AT) ?: 0L,
            isMember = uid in memberUids,
            isAdmin = uid == getString(Src.FIELD_ADMIN_UID),
            hasPendingRequest = uid in uidList(Src.FIELD_PENDING_UIDS),
            isBanned = uid in uidList(Src.FIELD_BANNED_UIDS)
        )
    }

    private fun DocumentSnapshot.toMember(adminUid: String?) = CommunityMember(
        uid = getString(Src.FIELD_UID) ?: id,
        name = getString(Src.FIELD_NAME) ?: "Member",
        avatarUrl = getString(Src.FIELD_AVATAR_URL),
        joinedAt = getLong(Src.FIELD_JOINED_AT) ?: 0L,
        isAdmin = (getString(Src.FIELD_UID) ?: id) == adminUid
    )

    private fun DocumentSnapshot.toPost(myReaction: Reaction?, canDelete: Boolean): CommunityPost {
        @Suppress("UNCHECKED_CAST")
        val counts = (get(Src.FIELD_REACTION_COUNTS) as? Map<String, Any?>).orEmpty()
        return CommunityPost(
            id = id,
            authorUid = getString(Src.FIELD_AUTHOR_UID).orEmpty(),
            authorName = getString(Src.FIELD_AUTHOR_NAME) ?: "Member",
            authorAvatarUrl = getString(Src.FIELD_AVATAR_URL),
            text = getString(Src.FIELD_TEXT).orEmpty(),
            imageUrl = getString(Src.FIELD_IMAGE_URL),
            imageId = getString(Src.FIELD_IMAGE_ID),
            createdAt = getLong(Src.FIELD_CREATED_AT) ?: 0L,
            reactionCounts = Reaction.entries.mapNotNull { reaction ->
                // A count can go negative if a decrement lands without its
                // matching increment; showing "-1 likes" is worse than showing
                // nothing, so anything at or below zero is simply absent.
                val count = (counts[reaction.storageName] as? Number)?.toInt() ?: 0
                if (count > 0) reaction to count else null
            }.toMap(),
            commentCount = (getLong(Src.FIELD_COMMENT_COUNT) ?: 0L).toInt().coerceAtLeast(0),
            myReaction = myReaction,
            canDelete = canDelete
        )
    }

    private fun DocumentSnapshot.uidList(field: String): List<String> =
        (get(field) as? List<*>)?.filterIsInstance<String>() ?: emptyList()

    // ------------------------------------------------------------- plumbing

    private fun myAvatarUrl(): String? =
        authRepository.currentUser?.uid?.let { userPreferences.getAvatarUrl(it) }

    /**
     * The name other members see. Never the email address: the part before the
     * @ is not something the user agreed to publish.
     */
    private fun AuthUser.communityName(): String =
        displayName?.trim()?.takeIf { it.isNotEmpty() } ?: "Member"

    /**
     * Runs [block] with the signed-in user, turning anything thrown into a
     * readable failure.
     */
    private suspend fun <T> call(block: suspend (AuthUser) -> T): Result<T> =
        withContext(Dispatchers.IO) {
            val user = authRepository.currentUser
                ?: return@withContext Result.failure(
                    IllegalStateException("Sign in to use communities.")
                )
            try {
                Result.success(block(user))
            } catch (e: Exception) {
                Result.failure(IllegalStateException(e.readableMessage(), e))
            }
        }

    private fun Exception.readableMessage(): String = when {
        this is FirebaseFirestoreException &&
            code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "You don't have permission to do that."

        this is FirebaseFirestoreException &&
            code == FirebaseFirestoreException.Code.UNAVAILABLE ->
            "No connection. Communities need to be online."

        this is IOException -> "No connection. Communities need to be online."
        this is HttpException -> "The server rejected that (${code()})."
        else -> message ?: "Something went wrong. Try again."
    }

    private companion object {
        const val JPEG = "image/jpeg"
        const val DEFAULT_ICON = "🏋️"
        /** Ids are six characters; anything longer is certainly a name. */
        const val ID_SEARCH_MAX = 8
    }
}

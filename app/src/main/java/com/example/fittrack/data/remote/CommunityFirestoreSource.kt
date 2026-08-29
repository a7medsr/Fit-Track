package com.example.fittrack.data.remote

import com.example.fittrack.domain.model.CommunityLimits
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Reads and writes the shared community tree.
 *
 * This is a different animal from [FirestoreDataSource], which mirrors one
 * user's private data as whole-list replaces. Nothing here may be written that
 * way: several people write to the same community at the same time, and a
 * whole-list replace would silently drop whatever landed in between. So every
 * shared value is either its own document or an atomic operation --
 * `arrayUnion`, `arrayRemove`, `increment`.
 */
@Singleton
class CommunityFirestoreSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    // ------------------------------------------------------------ documents

    fun communities() = firestore.collection(COMMUNITIES)

    fun community(cid: String) = communities().document(cid)

    fun members(cid: String) = community(cid).collection(MEMBERS)

    fun requests(cid: String) = community(cid).collection(REQUESTS)

    fun scores(cid: String) = community(cid).collection(SCORES)

    fun posts(cid: String) = community(cid).collection(POSTS)

    fun post(cid: String, postId: String) = posts(cid).document(postId)

    fun reactions(cid: String, postId: String) = post(cid, postId).collection(REACTIONS)

    fun comments(cid: String, postId: String) = post(cid, postId).collection(COMMENTS)

    // --------------------------------------------------------------- create

    /**
     * Claims a short id under a transaction, so two people creating a community
     * at the same moment cannot end up sharing one.
     *
     * The id doubles as the code people search for, which is why it is six
     * readable characters rather than a Firestore auto-id: nobody is going to
     * read `bK3nQ8zXyR2mLp0` down the phone to a friend.
     */
    suspend fun createWithUniqueId(fields: Map<String, Any?>): String {
        repeat(ID_ATTEMPTS) {
            val candidate = randomId()
            val doc = community(candidate)
            val claimed = firestore.runTransaction { txn ->
                if (txn.get(doc).exists()) {
                    false
                } else {
                    txn.set(doc, fields)
                    true
                }
            }.await()
            if (claimed) return candidate
        }
        // Six characters from a 32-letter alphabet is a billion combinations,
        // so five collisions in a row means something else is wrong.
        throw IllegalStateException("Could not allocate a community code. Try again.")
    }

    private fun randomId(): String =
        (1..ID_LENGTH).map { ID_ALPHABET[Random.nextInt(ID_ALPHABET.length)] }.joinToString("")

    // ------------------------------------------------------------ discovery

    suspend fun myCommunities(uid: String): QuerySnapshot =
        communities()
            .whereArrayContains(FIELD_MEMBER_UIDS, uid)
            .limit(CommunityLimits.MAX_COMMUNITIES_PER_USER.toLong())
            .get()
            .await()

    /** The busiest communities, for an empty search box. */
    suspend fun popular(): QuerySnapshot =
        communities()
            .orderBy(FIELD_MEMBER_COUNT, Query.Direction.DESCENDING)
            .limit(DIRECTORY_LIMIT)
            .get()
            .await()

    /**
     * Firestore has no substring search, so this is a prefix match on a
     * lowercased copy of the name. U+F8FF sorts above every character people
     * type, which is what turns a range query into "starts with".
     */
    suspend fun searchByName(prefix: String): QuerySnapshot =
        communities()
            .orderBy(FIELD_NAME_LOWER)
            .startAt(prefix)
            .endAt(prefix + HIGH_SORT_KEY)
            .limit(DIRECTORY_LIMIT)
            .get()
            .await()

    suspend fun byId(cid: String): DocumentSnapshot = community(cid).get().await()

    // ------------------------------------------------------------ membership

    /**
     * Asking to join is two writes that must both land: the pending flag on the
     * community, which the directory reads, and the request itself, which
     * carries the name and photo the admin needs to decide.
     */
    suspend fun addJoinRequest(cid: String, uid: String, name: String, avatarUrl: String?) {
        val batch = firestore.batch()
        batch.update(community(cid), FIELD_PENDING_UIDS, FieldValue.arrayUnion(uid))
        batch.set(
            requests(cid).document(uid),
            mapOf(
                FIELD_UID to uid,
                FIELD_NAME to name,
                FIELD_AVATAR_URL to avatarUrl,
                FIELD_REQUESTED_AT to System.currentTimeMillis()
            )
        )
        batch.commit().await()
    }

    suspend fun removeJoinRequest(cid: String, uid: String) {
        val batch = firestore.batch()
        batch.update(community(cid), FIELD_PENDING_UIDS, FieldValue.arrayRemove(uid))
        batch.delete(requests(cid).document(uid))
        batch.commit().await()
    }

    /**
     * Approving moves someone from pending to member and writes the roster row,
     * all in one transaction -- a half-applied approval would leave them
     * counted as a member with no name against it.
     *
     * A transaction rather than a batch of `arrayUnion` calls because
     * `memberCount` has to equal `memberUids.size()` exactly: it is what the
     * directory sorts on, and the security rules reject any write where the two
     * disagree. An increment cannot promise that; a value computed from the
     * array it was read with can.
     */
    suspend fun approve(cid: String, request: DocumentSnapshot) {
        val uid = request.getString(FIELD_UID) ?: return
        val name = request.getString(FIELD_NAME) ?: "Member"
        val avatarUrl = request.getString(FIELD_AVATAR_URL)

        firestore.runTransaction { txn ->
            val snapshot = txn.get(community(cid))
            val memberUids = snapshot.uidList(FIELD_MEMBER_UIDS)
            if (uid in memberUids) return@runTransaction null
            if (memberUids.size >= CommunityLimits.MAX_MEMBERS) {
                throw IllegalStateException("This community is full.")
            }

            val updated = memberUids + uid
            txn.update(
                community(cid),
                mapOf(
                    FIELD_MEMBER_UIDS to updated,
                    FIELD_PENDING_UIDS to snapshot.uidList(FIELD_PENDING_UIDS) - uid,
                    FIELD_MEMBER_COUNT to updated.size
                )
            )
            txn.set(
                members(cid).document(uid),
                mapOf(
                    FIELD_UID to uid,
                    FIELD_NAME to name,
                    FIELD_AVATAR_URL to avatarUrl,
                    FIELD_JOINED_AT to System.currentTimeMillis()
                )
            )
            txn.delete(requests(cid).document(uid))
            null
        }.await()
    }

    /**
     * Removal and banning are one write, deliberately. Doing them separately
     * leaves a gap in which the removed member can request again, and a
     * "remove" that can be undone by the person removed is not a removal.
     */
    suspend fun removeAndBan(cid: String, uid: String) {
        firestore.runTransaction { txn ->
            val snapshot = txn.get(community(cid))
            val remaining = snapshot.uidList(FIELD_MEMBER_UIDS) - uid
            txn.update(
                community(cid),
                mapOf(
                    FIELD_MEMBER_UIDS to remaining,
                    FIELD_PENDING_UIDS to snapshot.uidList(FIELD_PENDING_UIDS) - uid,
                    FIELD_BANNED_UIDS to (snapshot.uidList(FIELD_BANNED_UIDS) + uid).distinct(),
                    FIELD_MEMBER_COUNT to remaining.size
                )
            )
            txn.delete(members(cid).document(uid))
            null
        }.await()
    }

    suspend fun unban(cid: String, uid: String) {
        community(cid).update(FIELD_BANNED_UIDS, FieldValue.arrayRemove(uid)).await()
    }

    /**
     * A member taking themselves out. Their posts stay; their roster row and
     * their score do not.
     *
     * Two commits, not one: the community document and the member row are
     * governed by different rules, and a member may touch the community
     * document only for the narrow self-leave case.
     */
    suspend fun leave(cid: String, uid: String) {
        firestore.runTransaction { txn ->
            val snapshot = txn.get(community(cid))
            val remaining = snapshot.uidList(FIELD_MEMBER_UIDS) - uid
            txn.update(
                community(cid),
                mapOf(
                    FIELD_MEMBER_UIDS to remaining,
                    FIELD_MEMBER_COUNT to remaining.size
                )
            )
            null
        }.await()
        members(cid).document(uid).delete().await()
    }

    suspend fun transferAdmin(cid: String, toUid: String, toName: String) {
        community(cid).update(
            mapOf(
                FIELD_ADMIN_UID to toUid,
                FIELD_ADMIN_NAME to toName
            )
        ).await()
    }

    suspend fun setMetric(cid: String, metric: String) {
        community(cid).update(FIELD_METRIC, metric).await()
    }

    // ---------------------------------------------------------- leaderboard

    suspend fun weekScores(cid: String, weekId: String): QuerySnapshot =
        scores(cid)
            .whereEqualTo(FIELD_WEEK_ID, weekId)
            .limit(CommunityLimits.MAX_MEMBERS.toLong())
            .get()
            .await()

    suspend fun publishScore(
        cid: String,
        weekId: String,
        uid: String,
        name: String,
        avatarUrl: String?,
        value: Int
    ) {
        scores(cid).document("${weekId}_$uid").set(
            mapOf(
                FIELD_UID to uid,
                FIELD_WEEK_ID to weekId,
                FIELD_NAME to name,
                FIELD_AVATAR_URL to avatarUrl,
                FIELD_VALUE to value,
                FIELD_UPDATED_AT to System.currentTimeMillis()
            )
        ).await()
    }

    // ----------------------------------------------------------------- feed

    suspend fun postPage(cid: String, beforeCreatedAt: Long?): QuerySnapshot {
        var query = posts(cid)
            .orderBy(FIELD_CREATED_AT, Query.Direction.DESCENDING)
            .limit(CommunityLimits.PAGE_SIZE.toLong())
        if (beforeCreatedAt != null) query = query.startAfter(beforeCreatedAt)
        return query.get().await()
    }

    suspend fun createPost(cid: String, fields: Map<String, Any?>): String {
        val doc = posts(cid).document()
        doc.set(fields).await()
        return doc.id
    }

    /**
     * A post's comments and reactions are separate documents, and Firestore
     * does not cascade. Left alone they are unreachable but still stored and
     * still billed, so they are cleared first -- bounded, because an unbounded
     * delete loop on a phone is how a "delete post" tap hangs forever.
     */
    suspend fun deletePostAndChildren(cid: String, postId: String) {
        clearSubcollection(comments(cid, postId))
        clearSubcollection(reactions(cid, postId))
        post(cid, postId).delete().await()
    }

    private suspend fun clearSubcollection(collection: com.google.firebase.firestore.CollectionReference) {
        repeat(CLEANUP_PAGES) {
            val page = collection.limit(CLEANUP_PAGE_SIZE).get().await()
            if (page.isEmpty) return
            val batch = firestore.batch()
            page.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
            if (page.size() < CLEANUP_PAGE_SIZE) return
        }
    }

    // ------------------------------------------------------------ reactions

    suspend fun myReactions(cid: String, uid: String): QuerySnapshot =
        firestore.collectionGroup(REACTIONS)
            .whereEqualTo(FIELD_UID, uid)
            .whereEqualTo(FIELD_COMMUNITY_ID, cid)
            .limit(REACTION_LOOKUP_LIMIT)
            .get()
            .await()

    suspend fun currentReaction(cid: String, postId: String, uid: String): String? =
        reactions(cid, postId).document(uid).get().await().getString(FIELD_EMOJI)

    /**
     * Swapping a reaction has to decrement the old tally and increment the new
     * one in the same commit, or a reaction changed twice quickly leaves the
     * counts permanently wrong.
     */
    suspend fun setReaction(
        cid: String,
        postId: String,
        uid: String,
        previous: String?,
        next: String?
    ) {
        if (previous == next) return

        val batch = firestore.batch()
        val tallies = mutableMapOf<String, Any>()
        previous?.let { tallies["$FIELD_REACTION_COUNTS.$it"] = FieldValue.increment(-1) }
        next?.let { tallies["$FIELD_REACTION_COUNTS.$it"] = FieldValue.increment(1) }
        if (tallies.isNotEmpty()) batch.update(post(cid, postId), tallies)

        val doc = reactions(cid, postId).document(uid)
        if (next == null) {
            batch.delete(doc)
        } else {
            batch.set(
                doc,
                mapOf(
                    FIELD_UID to uid,
                    // Carried on the reaction so one query can fetch everything
                    // this user reacted to in this community, instead of one
                    // read per post on every feed page.
                    FIELD_COMMUNITY_ID to cid,
                    FIELD_POST_ID to postId,
                    FIELD_EMOJI to next
                )
            )
        }
        batch.commit().await()
    }

    // ------------------------------------------------------------- comments

    suspend fun commentPage(cid: String, postId: String, beforeCreatedAt: Long?): QuerySnapshot {
        var query = comments(cid, postId)
            .orderBy(FIELD_CREATED_AT, Query.Direction.DESCENDING)
            .limit(CommunityLimits.PAGE_SIZE.toLong())
        if (beforeCreatedAt != null) query = query.startAfter(beforeCreatedAt)
        return query.get().await()
    }

    suspend fun addComment(cid: String, postId: String, fields: Map<String, Any?>) {
        val batch = firestore.batch()
        batch.set(comments(cid, postId).document(), fields)
        batch.update(post(cid, postId), FIELD_COMMENT_COUNT, FieldValue.increment(1))
        batch.commit().await()
    }

    suspend fun deleteComment(cid: String, postId: String, commentId: String) {
        val batch = firestore.batch()
        batch.delete(comments(cid, postId).document(commentId))
        batch.update(post(cid, postId), FIELD_COMMENT_COUNT, FieldValue.increment(-1))
        batch.commit().await()
    }

    // -------------------------------------------------------------- delete

    /**
     * Deleting a whole community leaves its subcollections behind unless they
     * are cleared first, which on a phone is only realistic for a small group.
     * Posts go one at a time so their own children go with them.
     */
    suspend fun deleteCommunity(cid: String) {
        val allPosts = posts(cid).limit(CLEANUP_TOTAL_POSTS).get().await()
        allPosts.documents.forEach { deletePostAndChildren(cid, it.id) }
        clearSubcollection(members(cid))
        clearSubcollection(requests(cid))
        clearSubcollection(scores(cid))
        community(cid).delete().await()
    }

    /** Firestore hands arrays back as List<*>; anything not a string is noise. */
    private fun DocumentSnapshot.uidList(field: String): List<String> =
        (get(field) as? List<*>)?.filterIsInstance<String>() ?: emptyList()

    companion object {
        const val COMMUNITIES = "communities"
        const val MEMBERS = "members"
        const val REQUESTS = "requests"
        const val SCORES = "scores"
        const val POSTS = "posts"
        const val REACTIONS = "reactions"
        const val COMMENTS = "comments"

        const val FIELD_NAME = "name"
        const val FIELD_NAME_LOWER = "nameLower"
        const val FIELD_DESCRIPTION = "description"
        const val FIELD_ICON = "icon"
        const val FIELD_ADMIN_UID = "adminUid"
        const val FIELD_ADMIN_NAME = "adminName"
        const val FIELD_METRIC = "metric"
        const val FIELD_MEMBER_UIDS = "memberUids"
        const val FIELD_PENDING_UIDS = "pendingUids"
        const val FIELD_BANNED_UIDS = "bannedUids"
        const val FIELD_MEMBER_COUNT = "memberCount"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_UID = "uid"
        const val FIELD_AVATAR_URL = "avatarUrl"
        const val FIELD_JOINED_AT = "joinedAt"
        const val FIELD_REQUESTED_AT = "requestedAt"
        const val FIELD_WEEK_ID = "weekId"
        const val FIELD_VALUE = "value"
        const val FIELD_UPDATED_AT = "updatedAt"
        const val FIELD_AUTHOR_UID = "authorUid"
        const val FIELD_AUTHOR_NAME = "authorName"
        const val FIELD_TEXT = "text"
        const val FIELD_IMAGE_URL = "imageUrl"
        const val FIELD_IMAGE_ID = "imageId"
        const val FIELD_REACTION_COUNTS = "reactionCounts"
        const val FIELD_COMMENT_COUNT = "commentCount"
        const val FIELD_COMMUNITY_ID = "communityId"
        const val FIELD_POST_ID = "postId"
        const val FIELD_EMOJI = "emoji"

        // No I, O, 0 or 1: these get read aloud and typed in by hand.
        private const val ID_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private const val ID_LENGTH = 6
        private const val ID_ATTEMPTS = 5

        /** Sorts above anything a person types, making startAt/endAt a prefix match. */
        private const val HIGH_SORT_KEY = '\uF8FF'

        private const val DIRECTORY_LIMIT = 20L
        private const val REACTION_LOOKUP_LIMIT = 200L
        private const val CLEANUP_PAGE_SIZE = 100L
        private const val CLEANUP_PAGES = 10
        private const val CLEANUP_TOTAL_POSTS = 300L
    }
}

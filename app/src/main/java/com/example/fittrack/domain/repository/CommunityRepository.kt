package com.example.fittrack.domain.repository

import android.net.Uri
import com.example.fittrack.domain.model.Community
import com.example.fittrack.domain.model.CommunityMember
import com.example.fittrack.domain.model.CommunityMetric
import com.example.fittrack.domain.model.CommunityPost
import com.example.fittrack.domain.model.JoinRequest
import com.example.fittrack.domain.model.LeaderboardEntry
import com.example.fittrack.domain.model.PostComment
import com.example.fittrack.domain.model.Reaction
import com.example.fittrack.domain.model.WeeklyWinner

/**
 * Everything the community feature reads and writes.
 *
 * Every call returns [Result] rather than throwing: none of this works offline,
 * so failure is an ordinary outcome the UI has to show, not an exception worth
 * unwinding for. The messages inside a failure are already user-readable.
 *
 * Paging is by `createdAt` rather than by a Firestore cursor object, so nothing
 * from the Firestore SDK leaks above the data layer.
 */
interface CommunityRepository {

    // ------------------------------------------------------------ discovery

    /** The signed-in user's own communities. */
    suspend fun myCommunities(): Result<List<Community>>

    /**
     * The public directory. A blank [query] returns the busiest communities;
     * otherwise it matches an exact id or a name prefix.
     */
    suspend fun discover(query: String): Result<List<Community>>

    suspend fun community(communityId: String): Result<Community>

    // ------------------------------------------------------------ lifecycle

    suspend fun create(
        name: String,
        description: String,
        icon: String,
        metric: CommunityMetric
    ): Result<Community>

    suspend fun requestToJoin(communityId: String): Result<Unit>

    suspend fun withdrawRequest(communityId: String): Result<Unit>

    suspend fun leave(communityId: String): Result<Unit>

    suspend fun deleteCommunity(communityId: String): Result<Unit>

    // ---------------------------------------------------------------- admin

    suspend fun pendingRequests(communityId: String): Result<List<JoinRequest>>

    suspend fun approve(communityId: String, uid: String): Result<Unit>

    suspend fun reject(communityId: String, uid: String): Result<Unit>

    suspend fun members(communityId: String): Result<List<CommunityMember>>

    /** Removes and bans in one step, so removal is not undone by re-requesting. */
    suspend fun removeMember(communityId: String, uid: String): Result<Unit>

    suspend fun unban(communityId: String, uid: String): Result<Unit>

    suspend fun bannedMembers(communityId: String): Result<List<CommunityMember>>

    suspend fun transferAdmin(communityId: String, toUid: String): Result<Unit>

    suspend fun setMetric(communityId: String, metric: CommunityMetric): Result<Unit>

    // ---------------------------------------------------------- leaderboard

    suspend fun leaderboard(communityId: String): Result<Leaderboard>

    /**
     * Recomputes this device's weekly number from local data and publishes it.
     *
     * Called when a community screen opens rather than on a schedule: the score
     * is only ever as fresh as the last time its owner had the app in front of
     * them, which is why the board shows how stale each row is.
     */
    suspend fun publishMyScore(communityId: String): Result<Unit>

    // ----------------------------------------------------------------- feed

    suspend fun posts(communityId: String, beforeCreatedAt: Long? = null): Result<List<CommunityPost>>

    suspend fun createPost(communityId: String, text: String, image: Uri?): Result<Unit>

    suspend fun deletePost(communityId: String, post: CommunityPost): Result<Unit>

    /** A null [reaction] clears whatever the user had chosen. */
    suspend fun react(communityId: String, postId: String, reaction: Reaction?): Result<Unit>

    // ------------------------------------------------------------- comments

    suspend fun comments(
        communityId: String,
        postId: String,
        beforeCreatedAt: Long? = null
    ): Result<List<PostComment>>

    suspend fun addComment(communityId: String, postId: String, text: String): Result<Unit>

    suspend fun deleteComment(communityId: String, postId: String, commentId: String): Result<Unit>
}

/**
 * The current week's board, plus last week's winner.
 *
 * Only the week just gone is kept: older weeks are of no interest to anyone and
 * would grow the collection without bound.
 */
data class Leaderboard(
    val metric: CommunityMetric,
    val weekLabel: String,
    val entries: List<LeaderboardEntry>,
    val lastWeekWinner: WeeklyWinner?
)

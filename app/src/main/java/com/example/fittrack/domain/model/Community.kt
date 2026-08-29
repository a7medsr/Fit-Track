package com.example.fittrack.domain.model

/**
 * A group of users with a shared weekly leaderboard and a post feed.
 *
 * Unlike everything else in the app, this is not the user's own data: it is
 * shared, many people write to it, and it only exists online. Room is
 * deliberately not involved -- Firestore's own cache covers reading offline,
 * and a second source of truth for data the device does not own would only
 * create conflicts it has no way to resolve.
 */
data class Community(
    /** Also the join code: short, unambiguous, and what search matches on. */
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val adminUid: String,
    val adminName: String,
    val metric: CommunityMetric,
    val memberCount: Int,
    val createdAt: Long,
    /** True when the signed-in user is in [memberUids]. */
    val isMember: Boolean,
    val isAdmin: Boolean,
    /** True when this user has asked to join and is waiting on the admin. */
    val hasPendingRequest: Boolean = false,
    val isBanned: Boolean = false
) {
    val canPost: Boolean get() = isMember
}

/**
 * What the weekly leaderboard ranks on. The admin picks one per community.
 *
 * [storageName] is what goes in Firestore and is matched by the security rules,
 * so renaming one of these is a breaking change to published data, not a
 * cosmetic edit.
 */
enum class CommunityMetric(
    val storageName: String,
    val displayName: String,
    val unit: String
) {
    STEPS("steps", "Steps", "steps"),
    ACTIVE_MINUTES("minutes", "Active minutes", "min"),
    CALORIES("calories", "Calories", "kcal"),
    WORKOUTS("workouts", "Workouts", "sessions");

    companion object {
        val DEFAULT = STEPS

        fun fromStorage(value: String?): CommunityMetric =
            entries.firstOrNull { it.storageName.equals(value, ignoreCase = true) } ?: DEFAULT
    }
}

/** Someone in the group, as everyone else sees them. */
data class CommunityMember(
    val uid: String,
    val name: String,
    val avatarUrl: String?,
    val joinedAt: Long,
    val isAdmin: Boolean
)

/** Someone waiting for the admin to let them in. */
data class JoinRequest(
    val uid: String,
    val name: String,
    val avatarUrl: String?,
    val requestedAt: Long
)

/**
 * One row of the weekly board.
 *
 * [updatedAt] is shown rather than hidden: a score is published by that
 * member's own phone, so someone who has not opened the app for three days
 * looks stalled, and the timestamp is the only thing that explains why.
 */
data class LeaderboardEntry(
    val uid: String,
    val name: String,
    val avatarUrl: String?,
    val value: Int,
    val updatedAt: Long,
    val rank: Int,
    val isMe: Boolean
)

/** A week's finished board, kept only for the week just gone. */
data class WeeklyWinner(
    val name: String,
    val avatarUrl: String?,
    val value: Int,
    val metric: CommunityMetric
)

data class CommunityPost(
    val id: String,
    val authorUid: String,
    val authorName: String,
    val authorAvatarUrl: String?,
    val text: String,
    /** Absolute URL on the VPS; null when the post is text only. */
    val imageUrl: String?,
    /** The server-side id, needed to delete the file when the post goes. */
    val imageId: String?,
    val createdAt: Long,
    val reactionCounts: Map<Reaction, Int>,
    val commentCount: Int,
    /** What the signed-in user reacted with, if anything. */
    val myReaction: Reaction?,
    /** True when this user may delete it: their own post, or they are admin. */
    val canDelete: Boolean
) {
    val totalReactions: Int get() = reactionCounts.values.sum()
}

data class PostComment(
    val id: String,
    val authorUid: String,
    val authorName: String,
    val authorAvatarUrl: String?,
    val text: String,
    val createdAt: Long,
    val canDelete: Boolean
)

/**
 * The fixed set of reactions. Fixed on purpose: an open emoji picker would mean
 * an unbounded set of counter fields on every post document.
 */
enum class Reaction(val storageName: String, val emoji: String) {
    FLEX("flex", "💪"),
    HEART("heart", "❤️"),
    FIRE("fire", "🔥"),
    CLAP("clap", "👏");

    companion object {
        fun fromStorage(value: String?): Reaction? =
            entries.firstOrNull { it.storageName.equals(value, ignoreCase = true) }
    }
}

/** Limits the UI enforces before a write is attempted, mirroring the rules. */
object CommunityLimits {
    const val MAX_MEMBERS = 50
    const val MAX_COMMUNITIES_PER_USER = 10
    const val POST_TEXT_MAX = 500
    const val COMMENT_TEXT_MAX = 300
    const val NAME_MIN = 3
    const val NAME_MAX = 40
    const val DESCRIPTION_MAX = 140
    const val PAGE_SIZE = 20
}

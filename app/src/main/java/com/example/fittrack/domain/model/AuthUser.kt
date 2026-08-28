package com.example.fittrack.domain.model

/** The signed-in account, reduced to what the app actually needs. */
data class AuthUser(
    val uid: String,
    val email: String?,
    val displayName: String?
)

/** Result of an auth attempt. [Failure.message] is already user-readable. */
sealed class AuthOutcome {
    data class Success(val user: AuthUser) : AuthOutcome()
    data class Failure(val message: String) : AuthOutcome()
}

package com.example.fittrack.domain.model

/**
 * What the assistant decided to do. The model never touches the database: it
 * only emits one of these, which [com.example.fittrack.data.ai.ActionExecutor]
 * validates and runs.
 *
 * There are deliberately no delete actions. A misparse can then add noise the
 * user can remove by hand, but can never destroy logged history.
 */
sealed class AiAction {

    /** Changes state, so it needs an explicit confirmation before running. */
    sealed class Write : AiAction()

    /** Read-only, safe to run the moment it arrives. */
    sealed class Read : AiAction()

    data class SetStepGoal(val steps: Int) : Write()

    data class LogWorkout(val exerciseName: String, val minutes: Int) : Write()

    data class AddCustomExercise(
        val name: String,
        val category: String,
        val intensity: String,
        val icon: String?
    ) : Write()

    data class SetUserWeight(val kg: Int) : Write()

    data class QueryStats(val metric: String, val period: String) : Read()

    /** No action at all: the reply is the whole response. */
    object Answer : Read()

    val isWrite: Boolean get() = this is Write
}

/** How an action attached to a chat message is progressing. */
enum class ActionState { NONE, PENDING, DONE, CANCELLED, FAILED }

package com.example.fittrack.data.ai

import com.example.fittrack.domain.model.AiAction
import org.json.JSONObject

/**
 * The contract between the app and whichever model is behind [AiProvider].
 *
 * The model returns the action AND the user-facing reply in one JSON object.
 * There is no tool-call round trip: that pattern would double the request count
 * for every action, which is the opposite of what a free tier can afford.
 */
object AiProtocol {

    fun systemPrompt(contextLine: String): String = """
        You are the in-app assistant for FitTrack, a fitness tracker.

        You do three things:
        1. Answer questions about the user's own tracked data, using CONTEXT below.
        2. Carry out app actions by returning an action object.
        3. Answer general fitness and exercise questions.

        CONTEXT (this user's current data):
        $contextLine

        Reply with ONE JSON object and nothing else. Shape:
        {"action": "<name>", "args": { ... }, "reply": "<what to say to the user>"}

        Allowed actions:
        - "answer"             args: {}                       general reply, no app change
        - "set_step_goal"      args: {"steps": int}           1000-50000
        - "set_user_weight"    args: {"kg": int}              30-250
        - "log_workout"        args: {"exercise": string, "minutes": int}   1-300
        - "add_custom_exercise" args: {"name": string, "category": string, "intensity": string, "icon": string}
              category: Cardio | Strength | Flexibility | Sports
              intensity: Light | Moderate | Intense
              icon: a single emoji
        - "query_stats"        args: {"metric": string, "period": string}

        Rules:
        - Use "answer" whenever the user is only asking something. Never invent an action.
        - Spell numbers out as digits. "eight thousand steps" is {"steps": 8000}.
        - For "exercise", use the exact name from the FitTrack library when you can.
        - "reply" is what the user reads. Keep it under two short sentences,
          friendly, no markdown, no emoji spam.
        - For an action, write "reply" as if it has already happened; the app asks
          the user to confirm before anything changes.
        - You are not a medical professional. Do not diagnose, do not give medical
          advice, and do not prescribe nutrition for a medical condition. If asked,
          decline briefly and suggest speaking to a doctor or a qualified coach.
        - If a question is outside fitness, exercise or this app, say so briefly.
    """.trimIndent()

    /** Parsed model output: what to do, and what to show. */
    data class Decision(val action: AiAction, val reply: String)

    /**
     * Tolerant on purpose. Even in JSON mode a model can wrap output in a code
     * fence or add a stray sentence, and a parse failure that throws away a
     * paid-for response is worse than one that digs the object back out.
     */
    fun parse(raw: String): Decision? {
        val json = extractJson(raw) ?: return null
        val obj = runCatching { JSONObject(json) }.getOrNull() ?: return null

        val reply = obj.optString("reply").takeIf { it.isNotBlank() }
            ?: return null
        val args = obj.optJSONObject("args") ?: JSONObject()

        val action = when (obj.optString("action").lowercase().trim()) {
            "set_step_goal" -> args.optIntOrNull("steps")?.let { AiAction.SetStepGoal(it) }
            "set_user_weight" -> args.optIntOrNull("kg")?.let { AiAction.SetUserWeight(it) }
            "log_workout" -> {
                val exercise = args.optString("exercise").takeIf { it.isNotBlank() }
                val minutes = args.optIntOrNull("minutes")
                if (exercise != null && minutes != null) {
                    AiAction.LogWorkout(exercise, minutes)
                } else {
                    null
                }
            }
            "add_custom_exercise" -> {
                val name = args.optString("name").takeIf { it.isNotBlank() }
                if (name == null) {
                    null
                } else {
                    AiAction.AddCustomExercise(
                        name = name,
                        category = args.optString("category").ifBlank { "Strength" },
                        intensity = args.optString("intensity").ifBlank { "Moderate" },
                        icon = args.optString("icon").takeIf { it.isNotBlank() }
                    )
                }
            }
            "query_stats" -> AiAction.QueryStats(
                metric = args.optString("metric"),
                period = args.optString("period").ifBlank { "today" }
            )
            else -> AiAction.Answer
        } ?: AiAction.Answer // malformed args degrade to a plain reply, never a wrong write

        return Decision(action, reply)
    }

    private fun JSONObject.optIntOrNull(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        // A model may send 8000, "8000" or 8000.0; all three are the same number.
        (opt(key) as? Number)?.let { return it.toInt() }
        return optString(key).trim().toDoubleOrNull()?.toInt()
    }

    /** Pulls the outermost {...} out of a response that may carry extra text. */
    private fun extractJson(raw: String): String? {
        val trimmed = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```")
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        return if (start >= 0 && end > start) trimmed.substring(start, end + 1) else null
    }
}

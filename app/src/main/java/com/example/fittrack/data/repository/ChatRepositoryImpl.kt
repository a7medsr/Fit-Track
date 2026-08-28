package com.example.fittrack.data.repository

import com.example.fittrack.data.ai.ActionExecutor
import com.example.fittrack.data.ai.AnswerCachePolicy
import com.example.fittrack.data.ai.ActionResult
import com.example.fittrack.data.ai.AiProtocol
import com.example.fittrack.data.ai.ContextBuilder
import com.example.fittrack.data.ai.LocalIntent
import com.example.fittrack.data.ai.RateLimiter
import com.example.fittrack.data.ai.TierClassifier
import com.example.fittrack.data.ai.UserSnapshot
import com.example.fittrack.data.local.AiResponseCacheEntity
import com.example.fittrack.data.local.ChatMessageDao
import com.example.fittrack.data.local.ChatMessageEntity
import com.example.fittrack.data.remote.AiProvider
import com.example.fittrack.data.remote.AiRequest
import com.example.fittrack.data.remote.AiResult
import com.example.fittrack.data.remote.AiTurn
import com.example.fittrack.domain.model.ActionState
import com.example.fittrack.domain.model.AiAction
import com.example.fittrack.domain.model.ChatMessage
import com.example.fittrack.domain.model.ChatRole
import com.example.fittrack.domain.repository.AuthRepository
import com.example.fittrack.domain.repository.ChatError
import com.example.fittrack.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.json.JSONObject
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes every message through the cheapest tier that can serve it.
 *
 *   Tier 1  local answer from the repositories, zero API cost
 *   Tier 2  an action, one API call
 *   Tier 3  general fitness knowledge, one API call, cached
 */
@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val chatDao: ChatMessageDao,
    private val authRepository: AuthRepository,
    private val provider: AiProvider,
    private val contextBuilder: ContextBuilder,
    private val actionExecutor: ActionExecutor,
    private val rateLimiter: RateLimiter
) : ChatRepository {

    private val currentUserId: String
        get() = authRepository.currentUser?.uid ?: LOCAL_USER

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeMessages(): Flow<List<ChatMessage>> =
        authRepository.observeUser()
            .flatMapLatest { user ->
                chatDao.observeForUser(user?.uid ?: LOCAL_USER)
                    .map { rows -> rows.map { it.toDomain() } }
            }

    override suspend fun send(text: String): ChatError? {
        val userId = currentUserId
        val message = text.trim()
        if (message.isEmpty()) return null

        chatDao.insert(
            ChatMessageEntity(
                userId = userId,
                role = ChatRole.USER.name,
                text = message,
                timestamp = System.currentTimeMillis()
            )
        )

        // ---- Tier 1: answered locally, no network, works offline ----
        val localIntent = TierClassifier.classify(message)
        if (localIntent != null) {
            val snapshot = contextBuilder.snapshot()
            appendAssistant(userId, answerLocally(localIntent, snapshot))
            return null
        }

        // ---- Tier 3 cache: a repeat general question costs nothing ----
        val cacheKey = hash(message)
        cachedAnswer(cacheKey)?.let {
            appendAssistant(userId, it)
            return null
        }

        if (!provider.isConfigured) {
            return ChatError.NoApiKey
        }

        val snapshot = contextBuilder.snapshot()
        val history = chatDao.recentForUser(userId, HISTORY_WINDOW)
            .asReversed()
            .map { AiTurn(fromUser = it.role == ChatRole.USER.name, text = it.text) }

        rateLimiter.acquire()

        val result = provider.send(
            AiRequest(
                systemPrompt = AiProtocol.systemPrompt(contextBuilder.summarise(snapshot)),
                turns = history,
                expectJson = true
            )
        )

        val raw = when (result) {
            is AiResult.Ok -> result.text
            is AiResult.MissingKey -> return ChatError.NoApiKey
            is AiResult.Offline -> return ChatError.Offline
            is AiResult.RateLimited -> return ChatError.RateLimited
            is AiResult.Failed -> return ChatError.Upstream(result.message)
        }

        val decision = AiProtocol.parse(raw)
            ?: return ChatError.Upstream("The assistant sent something I couldn't read.")

        when (val action = decision.action) {
            is AiAction.Write -> {
                // Writes wait for an explicit tap: the model can misparse a
                // number, and a wrong goal or weight is silent until noticed.
                appendAssistant(
                    userId = userId,
                    text = decision.reply,
                    pendingAction = action,
                    state = ActionState.PENDING
                )
            }

            is AiAction.QueryStats -> {
                // Stats never need a second call; the snapshot already has them.
                appendAssistant(userId, decision.reply)
            }

            AiAction.Answer -> {
                appendAssistant(userId, decision.reply)
                cacheIfGeneral(cacheKey, message, decision.reply)
            }
        }
        return null
    }

    override suspend fun confirmAction(messageId: Long): ChatError? {
        val row = chatDao.getById(messageId) ?: return null
        val action = row.pendingActionJson?.let { decodeAction(it) } ?: return null

        return when (val result = actionExecutor.execute(action)) {
            is ActionResult.Done -> {
                chatDao.setActionState(messageId, ActionState.DONE.name)
                appendAssistant(row.userId, result.message)
                null
            }
            is ActionResult.Rejected -> {
                chatDao.setActionState(messageId, ActionState.FAILED.name)
                appendAssistant(row.userId, result.message)
                null
            }
        }
    }

    override suspend fun cancelAction(messageId: Long) {
        chatDao.setActionState(messageId, ActionState.CANCELLED.name)
    }

    override suspend fun clearHistory() {
        chatDao.clearForUser(currentUserId)
    }

    override suspend fun adoptLocalHistory(uid: String) {
        if (uid.isBlank() || uid == LOCAL_USER) return
        chatDao.reassignUser(LOCAL_USER, uid)
    }

    /** Human-readable text for a pending write, used by the confirm chip. */
    suspend fun describePending(action: AiAction): String = actionExecutor.describe(action)

    // ------------------------------------------------------------ Tier 1

    private fun answerLocally(intent: LocalIntent, s: UserSnapshot): String = when (intent) {
        LocalIntent.STEPS_REMAINING ->
            if (s.stepsRemaining == 0) {
                "You've hit your goal of ${s.goal} steps today. ${s.stepsToday} so far."
            } else {
                "${s.stepsRemaining} steps to go. You're on ${s.stepsToday} of ${s.goal} (${s.goalPercent}%)."
            }

        LocalIntent.STEPS_TODAY ->
            "${s.stepsToday} steps today, ${s.goalPercent}% of your ${s.goal} goal."

        LocalIntent.CURRENT_GOAL ->
            "Your daily goal is ${s.goal} steps. You're on ${s.stepsToday} today."

        LocalIntent.STREAK ->
            if (s.streak == 0) {
                "No active streak right now. Log something today to start one."
            } else {
                "You're on a ${s.streak}-day streak."
            }

        LocalIntent.CALORIES_TODAY ->
            "About ${s.caloriesToday} kcal burned today, including ${s.walkingCaloriesToday} from walking."

        LocalIntent.WORKOUTS_TODAY ->
            if (s.workoutsToday.isEmpty()) {
                "Nothing logged today yet, aside from your ${s.stepsToday} steps."
            } else {
                "Today: " + s.workoutsToday.joinToString(", ") { "${it.first} ${it.second} min" } + "."
            }

        LocalIntent.BODY_WEIGHT ->
            "Your body weight is set to ${s.weightKg} kg. Calorie estimates use it."

        LocalIntent.HELP ->
            "Ask me how far you are today, set your step goal or weight, log a workout, " +
                "add a custom exercise, or ask general fitness questions."
    }

    // ------------------------------------------------------------ helpers

    private suspend fun appendAssistant(
        userId: String,
        text: String,
        pendingAction: AiAction? = null,
        state: ActionState = ActionState.NONE
    ) {
        chatDao.insert(
            ChatMessageEntity(
                userId = userId,
                role = ChatRole.ASSISTANT.name,
                text = text,
                timestamp = System.currentTimeMillis(),
                pendingActionJson = pendingAction?.let { encodeAction(it) },
                actionState = state.name
            )
        )
    }

    private suspend fun cachedAnswer(hash: String): String? {
        val notBefore = System.currentTimeMillis() - CACHE_TTL_MS
        return chatDao.cachedAnswer(hash, notBefore)?.answer
    }

    private suspend fun cacheIfGeneral(hash: String, question: String, answer: String) {
        if (!AnswerCachePolicy.isCacheable(question)) return
        chatDao.cacheAnswer(
            AiResponseCacheEntity(
                questionHash = hash,
                answer = answer,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    private fun hash(question: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(AnswerCachePolicy.normalise(question).toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun encodeAction(action: AiAction): String {
        val obj = JSONObject()
        when (action) {
            is AiAction.SetStepGoal -> obj.put("type", "set_step_goal").put("steps", action.steps)
            is AiAction.SetUserWeight -> obj.put("type", "set_user_weight").put("kg", action.kg)
            is AiAction.LogWorkout -> obj.put("type", "log_workout")
                .put("exercise", action.exerciseName).put("minutes", action.minutes)
            is AiAction.AddCustomExercise -> obj.put("type", "add_custom_exercise")
                .put("name", action.name).put("category", action.category)
                .put("intensity", action.intensity).put("icon", action.icon ?: "")
            else -> obj.put("type", "answer")
        }
        return obj.toString()
    }

    private fun decodeAction(json: String): AiAction? {
        val obj = runCatching { JSONObject(json) }.getOrNull() ?: return null
        return when (obj.optString("type")) {
            "set_step_goal" -> AiAction.SetStepGoal(obj.optInt("steps"))
            "set_user_weight" -> AiAction.SetUserWeight(obj.optInt("kg"))
            "log_workout" -> AiAction.LogWorkout(obj.optString("exercise"), obj.optInt("minutes"))
            "add_custom_exercise" -> AiAction.AddCustomExercise(
                name = obj.optString("name"),
                category = obj.optString("category"),
                intensity = obj.optString("intensity"),
                icon = obj.optString("icon").takeIf { it.isNotBlank() }
            )
            else -> null
        }
    }

    private fun ChatMessageEntity.toDomain() = ChatMessage(
        id = id,
        role = if (role == ChatRole.USER.name) ChatRole.USER else ChatRole.ASSISTANT,
        text = text,
        timestamp = timestamp,
        pendingAction = pendingActionJson?.let { decodeAction(it) },
        actionState = runCatching { ActionState.valueOf(actionState) }
            .getOrDefault(ActionState.NONE)
    )

    companion object {
        const val LOCAL_USER = "local"
        /** Only the tail of the conversation is sent; full history is pure cost. */
        const val HISTORY_WINDOW = 6
        private const val CACHE_TTL_MS = 30L * 24 * 60 * 60 * 1000 // 30 days
    }
}

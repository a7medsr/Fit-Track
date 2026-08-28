package com.example.fittrack

import com.example.fittrack.data.ai.AiProtocol
import com.example.fittrack.domain.model.AiAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parsing model output is the single riskiest part of the feature: a wrong
 * parse silently performs the wrong write. These cover the shapes a model
 * actually produces, including the malformed ones.
 */
class AiProtocolTest {

    @Test
    fun `parses a clean action response`() {
        val decision = AiProtocol.parse(
            """{"action":"set_step_goal","args":{"steps":12000},"reply":"Goal set to 12,000."}"""
        )
        assertEquals(AiAction.SetStepGoal(12000), decision?.action)
        assertEquals("Goal set to 12,000.", decision?.reply)
    }

    @Test
    fun `survives a markdown code fence`() {
        val decision = AiProtocol.parse(
            "```json\n{\"action\":\"answer\",\"args\":{},\"reply\":\"Rest days matter.\"}\n```"
        )
        assertEquals(AiAction.Answer, decision?.action)
        assertEquals("Rest days matter.", decision?.reply)
    }

    @Test
    fun `survives prose around the object`() {
        val decision = AiProtocol.parse(
            "Sure! {\"action\":\"answer\",\"args\":{},\"reply\":\"Hydrate.\"} Hope that helps."
        )
        assertEquals("Hydrate.", decision?.reply)
    }

    @Test
    fun `accepts numbers sent as strings or decimals`() {
        assertEquals(
            AiAction.SetUserWeight(80),
            AiProtocol.parse(
                """{"action":"set_user_weight","args":{"kg":"80"},"reply":"Done."}"""
            )?.action
        )
        assertEquals(
            AiAction.SetStepGoal(9000),
            AiProtocol.parse(
                """{"action":"set_step_goal","args":{"steps":9000.0},"reply":"Done."}"""
            )?.action
        )
    }

    @Test
    fun `parses log workout with both fields`() {
        val action = AiProtocol.parse(
            """{"action":"log_workout","args":{"exercise":"Push-Ups","minutes":20},"reply":"Logged."}"""
        )?.action
        assertEquals(AiAction.LogWorkout("Push-Ups", 20), action)
    }

    @Test
    fun `a write missing its arguments degrades to a plain answer, never a wrong write`() {
        // Missing "minutes": must NOT become a workout of 0 minutes.
        val action = AiProtocol.parse(
            """{"action":"log_workout","args":{"exercise":"Running"},"reply":"Sure."}"""
        )?.action
        assertEquals(AiAction.Answer, action)
    }

    @Test
    fun `unknown action name falls back to answer`() {
        val action = AiProtocol.parse(
            """{"action":"delete_everything","args":{},"reply":"Nope."}"""
        )?.action
        assertEquals(AiAction.Answer, action)
    }

    @Test
    fun `garbage and missing reply return null`() {
        assertNull(AiProtocol.parse("not json at all"))
        assertNull(AiProtocol.parse(""))
        assertNull(AiProtocol.parse("""{"action":"answer","args":{}}"""))
    }

    @Test
    fun `writes are flagged as writes and reads are not`() {
        assertTrue(AiAction.SetStepGoal(5000).isWrite)
        assertTrue(AiAction.LogWorkout("Running", 30).isWrite)
        assertTrue(AiAction.SetUserWeight(70).isWrite)
        assertTrue(AiAction.AddCustomExercise("Padel", "Sports", "Intense", null).isWrite)
        assertTrue(!AiAction.Answer.isWrite)
        assertTrue(!AiAction.QueryStats("steps", "today").isWrite)
    }

    @Test
    fun `system prompt carries the user context line`() {
        val prompt = AiProtocol.systemPrompt("Goal 10000. Today 6432 (64%).")
        assertTrue(prompt.contains("Goal 10000. Today 6432 (64%)."))
        assertTrue(prompt.contains("not a medical professional"))
    }
}

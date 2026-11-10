package com.druggradientchip.android.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantGoldenCorpusTest {
    @Test
    fun `shared JSON corpus executes required cross-language behavior`() {
        val json = checkNotNull(javaClass.classLoader?.getResourceAsStream("assistant_golden_corpus.json"))
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val cases = Regex("\\{\\\"id\\\":\\\"([^\\\"]+)\\\",\\\"message\\\":\\\"([^\\\"]+)\\\",\\\"command\\\":\\\"([^\\\"]+)\\\"}")
            .findAll(json)
            .map { GoldenCase(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }
            .toList()
        val ids = cases.map(GoldenCase::id).toSet()
        assertEquals(18, ids.size)
        listOf(
            "forward_simple_en", "forward_multi_chip_zh", "forward_mixed_en", "context_continue_zh",
            "ordinal_en", "negative_width", "reverse_en", "reverse_inlet_before", "reverse_tail_inlet_zh",
            "reverse_missing_inlet", "reverse_wrong_count", "ambiguous_position", "malformed_number",
            "reset", "help", "confirm", "cancel",
        ).forEach { assertTrue("Missing golden case $it", it in ids) }

        val interpreter = AssistantInterpreter()
        val controller = AssistantSessionController(interpreter)
        cases.forEach { case ->
            val context = if (case.id == "context_continue_zh") {
                AssistantSessionState(activeMode = AssistantMode.FORWARD, activeChipPosition = 2)
            } else {
                AssistantSessionState()
            }
            val result = controller.process(context, case.message)
            when (case.command) {
                "FORWARD_UPDATE" -> assertTrue(case.id, result.command is AssistantCommand.ForwardUpdate)
                "REVERSE_UPDATE" -> assertTrue(case.id, result.command is AssistantCommand.ReverseUpdate && result.validationIssues.isEmpty())
                "FORWARD_UPDATE_INVALID", "REVERSE_UPDATE_INVALID" -> assertTrue(case.id, result.validationIssues.isNotEmpty())
                "REVERSE_UPDATE_INCOMPLETE" -> assertEquals(case.id, DraftStatus.INCOMPLETE, result.state.confirmationState)
                "CLARIFY" -> assertTrue(case.id, result.command is AssistantCommand.Clarify)
                "RESET" -> assertTrue(case.id, result.command is AssistantCommand.Reset)
                "HELP" -> assertTrue(case.id, result.command is AssistantCommand.Help)
                "CONFIRM_AND_RUN" -> assertTrue(case.id, result.command is AssistantCommand.ConfirmAndRun)
                "CANCEL" -> assertTrue(case.id, result.command is AssistantCommand.Cancel)
                else -> error("Unknown golden expectation ${case.command}")
            }
        }
    }

    private data class GoldenCase(val id: String, val message: String, val command: String)
}

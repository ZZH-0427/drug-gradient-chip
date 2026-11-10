package com.druggradientchip.android.assistant

import com.druggradientchip.android.reverse.HighConcentrationInlet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantSessionControllerTest {
    private val controller = AssistantSessionController()

    @Test
    fun `invalid engineering values retain actual extraction but do not mutate draft`() {
        val original = AssistantSessionState(activeMode = AssistantMode.FORWARD, activeChipPosition = 1)
        val result = controller.process(original, "left width -0.1")
        val command = result.command as AssistantCommand.ForwardUpdate
        assertEquals(-0.1f, command.chips.single().leftWidthMm)
        assertEquals(listOf(ValidationCode.INVALID_WIDTH), result.validationIssues.map(ValidationIssue::code))
        assertEquals(original.forwardDraft, result.state.forwardDraft)
        assertEquals(DraftStatus.INVALID, result.state.confirmationState)
    }

    @Test
    fun `unsupported count structure distance and wrong reverse count are rejected before draft mutation`() {
        assertEquals(ValidationCode.UNSUPPORTED_CHIP_COUNT, controller.process(AssistantSessionState(), "forward with 11 chips").validationIssues.single().code)
        assertEquals(ValidationCode.INVALID_CHIP_TYPE, controller.process(AssistantSessionState(activeChipPosition = 1), "chip 1 structure 7to1").validationIssues.single().code)
        assertEquals(ValidationCode.INVALID_DISTANCE, controller.process(AssistantSessionState(activeChipPosition = 1), "chip 1 left distance 0").validationIssues.single().code)
        assertEquals(
            ValidationCode.CHIP_POSITION_OUT_OF_RANGE,
            controller.process(AssistantSessionState(), "forward with 4 chips; chip 5 left width .1").validationIssues.single().code,
        )
        assertEquals(
            ValidationCode.REVERSE_REQUIRES_SIX_TARGETS,
            controller.process(AssistantSessionState(), "reverse target concentrations are 0.1 0.2 0.3 0.4 0.5").validationIssues.single().code,
        )
    }

    @Test
    fun `reverse needs six targets and explicit inlet before confirmation`() {
        var state = controller.process(AssistantSessionState(), "reverse target concentrations are .08 .14 .25 .38 .48 .58").state
        assertEquals(DraftStatus.INCOMPLETE, state.confirmationState)
        assertEquals(listOf(MissingField(MissingFieldCode.HIGH_INLET)), state.missingFields)
        state = controller.process(state, "Inlet2 high").state
        assertEquals(DraftStatus.READY_FOR_CONFIRMATION, state.confirmationState)
        assertEquals(HighConcentrationInlet.INLET_2, state.reverseDraft.highInlet)
    }

    @Test
    fun `forward exposes exact missing fields and becomes confirmation ready`() {
        var state = controller.process(AssistantSessionState(), "forward with 4 chips and Inlet1 high").state
        assertEquals(20, state.missingFields.size)
        for (position in 1..4) {
            state = controller.process(state, "chip $position structure 4to2, left width .1, right width .2, left distance 5, right distance 6").state
        }
        assertEquals(emptyList<MissingField>(), state.missingFields)
        assertEquals(DraftStatus.READY_FOR_CONFIRMATION, state.confirmationState)
    }

    @Test
    fun `ambiguous input leaves valid drafts unchanged`() {
        val state = controller.process(AssistantSessionState(), "forward with 4 chips and Inlet1 high").state
        val result = controller.process(state, "chip 1 and chip 2 left width .1")
        assertEquals(state.forwardDraft, result.state.forwardDraft)
        assertEquals(ClarificationReason.CONFLICTING_CHIP_POSITIONS, result.state.pendingAmbiguity)
    }

    @Test
    fun `reset clears only immutable assistant state while cancel help and confirm do not execute runtimes`() {
        val populated = controller.process(AssistantSessionState(), "forward with 4 chips and Inlet1 high").state
        listOf("cancel", "help", "confirm").forEach { message ->
            val result = controller.process(populated, message)
            assertEquals(populated.forwardDraft, result.state.forwardDraft)
        }
        val reset = controller.process(populated, "reset")
        assertTrue(reset.command is AssistantCommand.Reset)
        assertEquals(AssistantSessionState(), reset.state)
        assertNotSame(populated, reset.state)
    }

    @Test
    fun `independent session values never share draft mutations`() {
        val first = controller.process(AssistantSessionState(), "forward with 4 chips and Inlet1 high; chip 1 left width .1").state
        val second = controller.process(AssistantSessionState(), "forward with 5 chips and Inlet2 high; chip 2 right width .2").state
        assertEquals(setOf(1), first.forwardDraft.chips.keys)
        assertEquals(setOf(2), second.forwardDraft.chips.keys)
    }
}

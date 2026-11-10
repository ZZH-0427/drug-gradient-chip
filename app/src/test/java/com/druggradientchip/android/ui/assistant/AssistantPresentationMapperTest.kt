package com.druggradientchip.android.ui.assistant

import com.druggradientchip.android.R
import com.druggradientchip.android.assistant.AssistantCommand
import com.druggradientchip.android.assistant.AssistantExecutionError
import com.druggradientchip.android.assistant.AssistantExecutionErrorCode
import com.druggradientchip.android.assistant.AssistantExecutionState
import com.druggradientchip.android.assistant.AssistantExecutionStatus
import com.druggradientchip.android.assistant.ClarificationReason
import com.druggradientchip.android.assistant.HelpCategory
import com.druggradientchip.android.assistant.HelpExample
import com.druggradientchip.android.assistant.MissingField
import com.druggradientchip.android.assistant.MissingFieldCode
import com.druggradientchip.android.reverse.HighConcentrationInlet
import com.druggradientchip.android.ui.UiText
import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantPresentationMapperTest {
    private val mapper = AssistantPresentationMapper()

    @Test
    fun `stable execution states map to localized resource identifiers`() {
        assertResource(R.string.assistant_status_ready, mapper.status(AssistantExecutionStatus.IDLE).text)
        assertResource(R.string.assistant_status_collecting, mapper.status(AssistantExecutionStatus.COLLECTING).text)
        assertResource(R.string.assistant_status_confirmation, mapper.status(AssistantExecutionStatus.READY_FOR_CONFIRMATION).text)
        assertResource(R.string.assistant_status_running_reverse, mapper.status(AssistantExecutionStatus.RUNNING_REVERSE).text)
        assertResource(R.string.assistant_status_success, mapper.status(AssistantExecutionStatus.COMPLETED_FORWARD).text)
        assertResource(R.string.assistant_status_canceled, mapper.status(AssistantExecutionStatus.CANCELED).text)
        assertResource(R.string.assistant_status_error, mapper.status(AssistantExecutionStatus.ERROR).text)
    }

    @Test
    fun `inlets and positioned missing fields preserve structured meaning`() {
        assertResource(R.string.assistant_inlet_one, mapper.inlet(HighConcentrationInlet.INLET_1))
        assertResource(R.string.assistant_inlet_two, mapper.inlet(HighConcentrationInlet.INLET_2))
        val missing = mapper.missing(MissingField(MissingFieldCode.RIGHT_WIDTH, chipPosition = 6)) as UiText.Resource
        assertEquals(R.string.assistant_missing_right_width, missing.id)
        assertEquals(listOf(6), missing.args)
    }

    @Test
    fun `clarification error and help remain resource based`() {
        assertResource(
            R.string.assistant_clarify_malformed_number,
            mapper.clarification(ClarificationReason.MALFORMED_NUMBER),
        )
        assertResource(R.string.assistant_error_busy, mapper.error(AssistantExecutionErrorCode.BUSY))
        val help = mapper.response(
            AssistantExecutionState(
                lastCommand = AssistantCommand.Help(
                    HelpCategory.REVERSE,
                    listOf(HelpExample.REVERSE_TARGETS_AND_INLET),
                ),
            ),
        )
        assertEquals(2, help.size)
        assertResource(R.string.assistant_help_intro, help[0])
        assertResource(R.string.assistant_help_reverse, help[1])
    }

    @Test
    fun `typed execution errors do not expose throwable text`() {
        val response = mapper.response(
            AssistantExecutionState(
                status = AssistantExecutionStatus.ERROR,
                error = AssistantExecutionError(AssistantExecutionErrorCode.MODEL_ERROR),
            ),
        ).single()
        assertResource(R.string.assistant_error_model, response)
    }

    private fun assertResource(expected: Int, actual: UiText) {
        assertEquals(expected, (actual as UiText.Resource).id)
    }
}

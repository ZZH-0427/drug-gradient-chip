package com.druggradientchip.android.ui.assistant

import com.druggradientchip.android.R
import com.druggradientchip.android.assistant.AssistantCommand
import com.druggradientchip.android.assistant.AssistantExecutionErrorCode
import com.druggradientchip.android.assistant.AssistantExecutionNotice
import com.druggradientchip.android.assistant.AssistantExecutionState
import com.druggradientchip.android.assistant.AssistantExecutionStatus
import com.druggradientchip.android.assistant.ClarificationReason
import com.druggradientchip.android.assistant.HelpExample
import com.druggradientchip.android.assistant.MissingField
import com.druggradientchip.android.assistant.MissingFieldCode
import com.druggradientchip.android.reverse.HighConcentrationInlet
import com.druggradientchip.android.ui.UiText
import com.druggradientchip.android.ui.uiText

data class AssistantStatusPresentation(
    val text: UiText,
    val background: Int,
    val color: Int,
)

class AssistantPresentationMapper {
    fun status(status: AssistantExecutionStatus): AssistantStatusPresentation = when (status) {
        AssistantExecutionStatus.IDLE -> ready(R.string.assistant_status_ready)
        AssistantExecutionStatus.COLLECTING -> running(R.string.assistant_status_collecting)
        AssistantExecutionStatus.READY_FOR_CONFIRMATION -> ready(R.string.assistant_status_confirmation)
        AssistantExecutionStatus.RUNNING_FORWARD -> running(R.string.assistant_status_running_forward)
        AssistantExecutionStatus.RUNNING_REVERSE -> running(R.string.assistant_status_running_reverse)
        AssistantExecutionStatus.COMPLETED_FORWARD,
        AssistantExecutionStatus.COMPLETED_REVERSE -> ready(R.string.assistant_status_success)
        AssistantExecutionStatus.CANCELED -> warning(R.string.assistant_status_canceled)
        AssistantExecutionStatus.ERROR -> error(R.string.assistant_status_error)
    }

    fun inlet(inlet: HighConcentrationInlet?): UiText = when (inlet) {
        HighConcentrationInlet.INLET_1 -> uiText(R.string.assistant_inlet_one)
        HighConcentrationInlet.INLET_2 -> uiText(R.string.assistant_inlet_two)
        null -> uiText(R.string.assistant_value_missing)
    }

    fun missing(field: MissingField): UiText = when (field.code) {
        MissingFieldCode.MODE -> uiText(R.string.assistant_missing_mode)
        MissingFieldCode.CHIP_COUNT -> uiText(R.string.assistant_missing_chip_count)
        MissingFieldCode.HIGH_INLET -> uiText(R.string.assistant_missing_high_inlet)
        MissingFieldCode.CHIP_TYPE -> uiText(R.string.assistant_missing_chip_type, field.chipPosition ?: 0)
        MissingFieldCode.LEFT_WIDTH -> uiText(R.string.assistant_missing_left_width, field.chipPosition ?: 0)
        MissingFieldCode.RIGHT_WIDTH -> uiText(R.string.assistant_missing_right_width, field.chipPosition ?: 0)
        MissingFieldCode.LEFT_DISTANCE -> uiText(R.string.assistant_missing_left_distance, field.chipPosition ?: 0)
        MissingFieldCode.RIGHT_DISTANCE -> uiText(R.string.assistant_missing_right_distance, field.chipPosition ?: 0)
        MissingFieldCode.TARGET_CONCENTRATIONS -> uiText(R.string.assistant_missing_targets)
    }

    fun response(state: AssistantExecutionState): List<UiText> {
        val command = state.lastCommand
        if (command is AssistantCommand.Help) {
            return listOf(uiText(R.string.assistant_help_intro)) + command.examples.map(::help)
        }
        if (command is AssistantCommand.Clarify) return listOf(clarification(command.reason))
        state.error?.let { return listOf(error(it.code)) }
        state.notice?.let { return listOf(notice(it)) }
        return listOf(
            when (state.status) {
                AssistantExecutionStatus.IDLE -> uiText(R.string.assistant_status_ready)
                AssistantExecutionStatus.COLLECTING -> if (state.session.missingFields.isEmpty()) {
                    uiText(R.string.assistant_history_updated)
                } else {
                    uiText(R.string.assistant_history_missing)
                }
                AssistantExecutionStatus.READY_FOR_CONFIRMATION -> uiText(R.string.assistant_history_confirm)
                AssistantExecutionStatus.RUNNING_FORWARD -> uiText(R.string.assistant_history_running_forward)
                AssistantExecutionStatus.RUNNING_REVERSE -> uiText(R.string.assistant_history_running_reverse)
                AssistantExecutionStatus.COMPLETED_FORWARD -> uiText(R.string.assistant_history_forward_complete)
                AssistantExecutionStatus.COMPLETED_REVERSE -> uiText(R.string.assistant_history_reverse_complete)
                AssistantExecutionStatus.CANCELED -> uiText(R.string.assistant_history_canceled)
                AssistantExecutionStatus.ERROR -> uiText(R.string.assistant_error_invalid)
            },
        )
    }

    fun clarification(reason: ClarificationReason): UiText = uiText(
        when (reason) {
            ClarificationReason.EMPTY_MESSAGE -> R.string.assistant_clarify_empty
            ClarificationReason.UNSUPPORTED_OR_AMBIGUOUS -> R.string.assistant_clarify_unsupported
            ClarificationReason.CONFLICTING_MODE -> R.string.assistant_clarify_conflicting_mode
            ClarificationReason.CONFLICTING_INLET -> R.string.assistant_clarify_conflicting_inlet
            ClarificationReason.CHIP_POSITION_REQUIRED -> R.string.assistant_clarify_chip_position
            ClarificationReason.CONFLICTING_CHIP_POSITIONS -> R.string.assistant_clarify_conflicting_positions
            ClarificationReason.REPEATED_FIELD_CONFLICT -> R.string.assistant_clarify_repeated_field
            ClarificationReason.MALFORMED_NUMBER -> R.string.assistant_clarify_malformed_number
        },
    )

    fun error(code: AssistantExecutionErrorCode): UiText = uiText(
        when (code) {
            AssistantExecutionErrorCode.INCOMPLETE_DRAFT -> R.string.assistant_error_incomplete
            AssistantExecutionErrorCode.INVALID_DRAFT -> R.string.assistant_error_invalid
            AssistantExecutionErrorCode.CONFIRMATION_REQUIRED -> R.string.assistant_error_confirmation
            AssistantExecutionErrorCode.BUSY -> R.string.assistant_error_busy
            AssistantExecutionErrorCode.FORWARD_VALIDATION_FAILURE -> R.string.assistant_error_forward_validation
            AssistantExecutionErrorCode.FORWARD_INFERENCE_FAILURE -> R.string.assistant_error_forward_inference
            AssistantExecutionErrorCode.REVERSE_VALIDATION_FAILURE -> R.string.assistant_error_reverse_validation
            AssistantExecutionErrorCode.REVERSE_SEARCH_FAILURE -> R.string.assistant_error_reverse_search
            AssistantExecutionErrorCode.CANCELED -> R.string.assistant_error_canceled
            AssistantExecutionErrorCode.MODEL_ERROR -> R.string.assistant_error_model
            AssistantExecutionErrorCode.UNSUPPORTED_RUNTIME -> R.string.assistant_error_runtime
        },
    )

    private fun notice(notice: AssistantExecutionNotice): UiText = uiText(
        when (notice) {
            AssistantExecutionNotice.NO_ACTIVE_TASK -> R.string.assistant_history_no_task
            AssistantExecutionNotice.FORWARD_NOT_CANCELABLE -> R.string.assistant_history_forward_not_cancelable
            AssistantExecutionNotice.CANCELLATION_REQUESTED -> R.string.assistant_history_cancel_requested
        },
    )

    private fun help(example: HelpExample): UiText = uiText(
        when (example) {
            HelpExample.FORWARD_GLOBAL_PARAMETERS -> R.string.assistant_help_forward_global
            HelpExample.FORWARD_CHIP_PARAMETERS -> R.string.assistant_help_forward_chip
            HelpExample.FORWARD_CONTEXT_CONTINUATION -> R.string.assistant_help_forward_context
            HelpExample.REVERSE_TARGETS_AND_INLET -> R.string.assistant_help_reverse
        },
    )

    private fun ready(text: Int) = AssistantStatusPresentation(uiText(text), R.drawable.bg_status_ready, R.color.status_ready_text)
    private fun running(text: Int) = AssistantStatusPresentation(uiText(text), R.drawable.bg_status_running, R.color.status_running_text)
    private fun warning(text: Int) = AssistantStatusPresentation(uiText(text), R.drawable.bg_status_warning, R.color.status_warning_text)
    private fun error(text: Int) = AssistantStatusPresentation(uiText(text), R.drawable.bg_status_error, R.color.status_error_text)
}

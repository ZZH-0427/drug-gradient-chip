package com.druggradientchip.android.assistant

import com.druggradientchip.android.forward.ForwardRequest
import com.druggradientchip.android.forward.ForwardResult
import com.druggradientchip.android.reverse.ReverseProgress
import com.druggradientchip.android.reverse.ReverseRequest
import com.druggradientchip.android.reverse.ReverseResult

enum class AssistantExecutionStatus {
    IDLE,
    COLLECTING,
    READY_FOR_CONFIRMATION,
    RUNNING_FORWARD,
    RUNNING_REVERSE,
    COMPLETED_FORWARD,
    COMPLETED_REVERSE,
    CANCELED,
    ERROR,
}

enum class AssistantExecutionErrorCode {
    INCOMPLETE_DRAFT,
    INVALID_DRAFT,
    CONFIRMATION_REQUIRED,
    BUSY,
    FORWARD_VALIDATION_FAILURE,
    FORWARD_INFERENCE_FAILURE,
    REVERSE_VALIDATION_FAILURE,
    REVERSE_SEARCH_FAILURE,
    CANCELED,
    MODEL_ERROR,
    UNSUPPORTED_RUNTIME,
}

enum class AssistantExecutionNotice {
    NO_ACTIVE_TASK,
    FORWARD_NOT_CANCELABLE,
    CANCELLATION_REQUESTED,
}

data class AssistantExecutionError(
    val code: AssistantExecutionErrorCode,
    val forwardCode: com.druggradientchip.android.forward.ForwardErrorCode? = null,
    val reverseCode: com.druggradientchip.android.reverse.ReverseErrorCode? = null,
)

sealed interface AssistantExecutionResult {
    val mode: AssistantMode
    val warnings: List<String>

    data class Forward(
        val request: ForwardRequest,
        val productResult: ForwardResult,
        override val warnings: List<String> = emptyList(),
    ) : AssistantExecutionResult {
        override val mode = AssistantMode.FORWARD
    }

    data class Reverse(
        val request: ReverseRequest,
        val productResult: ReverseResult,
    ) : AssistantExecutionResult {
        override val mode = AssistantMode.REVERSE
        override val warnings: List<String> = productResult.warnings
    }
}

data class AssistantExecutionState(
    val session: AssistantSessionState = AssistantSessionState(),
    val status: AssistantExecutionStatus = AssistantExecutionStatus.IDLE,
    val result: AssistantExecutionResult? = null,
    val resultStale: Boolean = false,
    val reverseProgress: ReverseProgress? = null,
    val error: AssistantExecutionError? = null,
    val notice: AssistantExecutionNotice? = null,
    val lastCommand: AssistantCommand? = null,
    val validationIssues: List<ValidationIssue> = emptyList(),
)

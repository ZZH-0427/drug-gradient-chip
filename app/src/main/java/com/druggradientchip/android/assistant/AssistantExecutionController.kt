package com.druggradientchip.android.assistant

import com.druggradientchip.android.forward.ForwardErrorCode
import com.druggradientchip.android.forward.ForwardRuntimeException
import com.druggradientchip.android.reverse.ReverseErrorCode
import com.druggradientchip.android.reverse.ReverseTaskState

class AssistantExecutionController(
    private val bridge: AssistantRuntimeBridge,
    private val sessionController: AssistantSessionController = AssistantSessionController(),
    private val requestMapper: AssistantRequestMapper = AssistantRequestMapper(),
) {
    private val lock = Any()
    private var generation = 0L
    private var assistantOwnsReverse = false

    @Volatile
    var state: AssistantExecutionState = AssistantExecutionState()
        private set

    fun submit(message: String, onState: (AssistantExecutionState) -> Unit = {}): AssistantExecutionState = synchronized(lock) {
        val parsed = sessionController.process(state.session, message)
        when (parsed.command) {
            AssistantCommand.Cancel -> handleCancel(parsed.command, onState)
            AssistantCommand.Reset -> handleReset(parsed.command, onState)
            AssistantCommand.ConfirmAndRun -> handleConfirmation(parsed, onState)
            is AssistantCommand.Help -> publish(state.copy(lastCommand = parsed.command, error = null, notice = null), onState)
            else -> handleDraftUpdate(parsed, onState)
        }
        state
    }

    private fun handleDraftUpdate(update: SessionUpdateResult, observer: (AssistantExecutionState) -> Unit) {
        if (isRunning()) {
            publish(state.copy(lastCommand = update.command, error = AssistantExecutionError(AssistantExecutionErrorCode.BUSY), notice = null), observer)
            return
        }
        val changed = update.state.forwardDraft != state.session.forwardDraft ||
            update.state.reverseDraft != state.session.reverseDraft || update.state.activeMode != state.session.activeMode
        val error = when {
            update.command is AssistantCommand.Clarify -> AssistantExecutionError(AssistantExecutionErrorCode.INVALID_DRAFT)
            update.validationIssues.isNotEmpty() -> AssistantExecutionError(AssistantExecutionErrorCode.INVALID_DRAFT)
            else -> null
        }
        val status = when {
            error != null -> AssistantExecutionStatus.ERROR
            update.state.confirmationState == DraftStatus.READY_FOR_CONFIRMATION -> AssistantExecutionStatus.READY_FOR_CONFIRMATION
            else -> AssistantExecutionStatus.COLLECTING
        }
        publish(
            state.copy(
                session = update.state,
                status = status,
                resultStale = state.result != null && (state.resultStale || changed),
                error = error,
                notice = null,
                lastCommand = update.command,
                validationIssues = update.validationIssues,
                reverseProgress = null,
            ),
            observer,
        )
    }

    private fun handleConfirmation(update: SessionUpdateResult, observer: (AssistantExecutionState) -> Unit) {
        if (isRunning() || bridge.reverseRunning) {
            publish(state.copy(lastCommand = update.command, error = AssistantExecutionError(AssistantExecutionErrorCode.BUSY), notice = null), observer)
            return
        }
        if (state.session.confirmationState == DraftStatus.INVALID) {
            publish(state.copy(lastCommand = update.command, status = AssistantExecutionStatus.ERROR, error = AssistantExecutionError(AssistantExecutionErrorCode.INVALID_DRAFT), notice = null), observer)
            return
        }
        if (update.state.confirmationState != DraftStatus.READY_FOR_CONFIRMATION) {
            publish(
                state.copy(
                    session = update.state,
                    status = AssistantExecutionStatus.COLLECTING,
                    error = AssistantExecutionError(AssistantExecutionErrorCode.INCOMPLETE_DRAFT),
                    notice = null,
                    lastCommand = update.command,
                ),
                observer,
            )
            return
        }
        when (update.state.activeMode) {
            AssistantMode.FORWARD -> executeForward(update.state, update.command, observer)
            AssistantMode.REVERSE -> executeReverse(update.state, update.command, observer)
            null -> publish(state.copy(status = AssistantExecutionStatus.ERROR, error = AssistantExecutionError(AssistantExecutionErrorCode.CONFIRMATION_REQUIRED)), observer)
        }
    }

    private fun executeForward(session: AssistantSessionState, command: AssistantCommand, observer: (AssistantExecutionState) -> Unit) {
        val request = try {
            requestMapper.forward(session.forwardDraft)
        } catch (error: Throwable) {
            publish(state.copy(session = session, status = AssistantExecutionStatus.ERROR, error = mapForwardError(error), lastCommand = command), observer)
            return
        }
        val token = ++generation
        publish(state.copy(session = session, status = AssistantExecutionStatus.RUNNING_FORWARD, error = null, notice = null, lastCommand = command, reverseProgress = null), observer)
        if (!bridge.executeForward(request) { outcome ->
                synchronized(lock) {
                    if (token != generation) return@synchronized
                    outcome.fold(
                        onSuccess = { result -> publish(state.copy(status = AssistantExecutionStatus.COMPLETED_FORWARD, result = AssistantExecutionResult.Forward(request, result), resultStale = false, error = null), observer) },
                        onFailure = { error -> publish(state.copy(status = AssistantExecutionStatus.ERROR, error = mapForwardError(error)), observer) },
                    )
                }
            }
        ) {
            publish(state.copy(status = AssistantExecutionStatus.ERROR, error = AssistantExecutionError(AssistantExecutionErrorCode.BUSY)), observer)
        }
    }

    private fun executeReverse(session: AssistantSessionState, command: AssistantCommand, observer: (AssistantExecutionState) -> Unit) {
        val request = try {
            requestMapper.reverse(session.reverseDraft)
        } catch (error: Throwable) {
            publish(state.copy(session = session, status = AssistantExecutionStatus.ERROR, error = mapReverseThrowable(error), lastCommand = command), observer)
            return
        }
        val token = ++generation
        assistantOwnsReverse = true
        publish(state.copy(session = session, status = AssistantExecutionStatus.RUNNING_REVERSE, error = null, notice = null, lastCommand = command, reverseProgress = null), observer)
        val accepted = bridge.startReverse(request) { taskState ->
            synchronized(lock) {
                if (token != generation) return@synchronized
                when (taskState) {
                    ReverseTaskState.Idle, ReverseTaskState.Validating -> Unit
                    is ReverseTaskState.Running -> publish(state.copy(status = AssistantExecutionStatus.RUNNING_REVERSE, reverseProgress = taskState.progress), observer)
                    is ReverseTaskState.Completed -> {
                        assistantOwnsReverse = false
                        publish(state.copy(status = AssistantExecutionStatus.COMPLETED_REVERSE, result = AssistantExecutionResult.Reverse(request, taskState.result), resultStale = false, reverseProgress = null, error = null), observer)
                    }
                    ReverseTaskState.Canceled -> {
                        assistantOwnsReverse = false
                        publish(state.copy(status = AssistantExecutionStatus.CANCELED, reverseProgress = null, error = AssistantExecutionError(AssistantExecutionErrorCode.CANCELED)), observer)
                    }
                    is ReverseTaskState.Error -> {
                        assistantOwnsReverse = false
                        publish(state.copy(status = AssistantExecutionStatus.ERROR, reverseProgress = null, error = mapReverseError(taskState.code)), observer)
                    }
                }
            }
        }
        if (!accepted) {
            assistantOwnsReverse = false
            publish(state.copy(status = AssistantExecutionStatus.ERROR, error = AssistantExecutionError(AssistantExecutionErrorCode.BUSY)), observer)
        }
    }

    private fun handleCancel(command: AssistantCommand, observer: (AssistantExecutionState) -> Unit) {
        when {
            state.status == AssistantExecutionStatus.RUNNING_FORWARD ->
                publish(state.copy(lastCommand = command, notice = AssistantExecutionNotice.FORWARD_NOT_CANCELABLE, error = null), observer)
            assistantOwnsReverse && bridge.cancelReverse() ->
                publish(state.copy(lastCommand = command, notice = AssistantExecutionNotice.CANCELLATION_REQUESTED, error = null), observer)
            else -> publish(state.copy(lastCommand = command, notice = AssistantExecutionNotice.NO_ACTIVE_TASK, error = null), observer)
        }
    }

    private fun handleReset(command: AssistantCommand, observer: (AssistantExecutionState) -> Unit) {
        generation++
        if (assistantOwnsReverse) bridge.cancelReverse()
        assistantOwnsReverse = false
        val reset = sessionController.process(state.session, "reset")
        publish(AssistantExecutionState(session = reset.state, lastCommand = command), observer)
    }

    private fun isRunning() = state.status == AssistantExecutionStatus.RUNNING_FORWARD || state.status == AssistantExecutionStatus.RUNNING_REVERSE

    private fun mapForwardError(error: Throwable): AssistantExecutionError {
        val typed = error as? ForwardRuntimeException
        val code = when (typed?.code) {
            ForwardErrorCode.INVALID_REQUEST, ForwardErrorCode.UNSUPPORTED_CHIP_COUNT -> AssistantExecutionErrorCode.FORWARD_VALIDATION_FAILURE
            ForwardErrorCode.MISSING_MODEL_ASSET, ForwardErrorCode.MODEL_LOAD_FAILURE -> AssistantExecutionErrorCode.MODEL_ERROR
            ForwardErrorCode.UNSUPPORTED_DEVICE_RUNTIME -> AssistantExecutionErrorCode.UNSUPPORTED_RUNTIME
            else -> AssistantExecutionErrorCode.FORWARD_INFERENCE_FAILURE
        }
        return AssistantExecutionError(code, forwardCode = typed?.code)
    }

    private fun mapReverseThrowable(error: Throwable): AssistantExecutionError {
        val typed = error as? com.druggradientchip.android.reverse.ReverseRuntimeException
        return typed?.let { mapReverseError(it.code) }
            ?: AssistantExecutionError(AssistantExecutionErrorCode.REVERSE_VALIDATION_FAILURE)
    }

    private fun mapReverseError(code: ReverseErrorCode): AssistantExecutionError {
        val mapped = when (code) {
            ReverseErrorCode.INVALID_REQUEST, ReverseErrorCode.INVALID_TARGET, ReverseErrorCode.INVALID_INLET -> AssistantExecutionErrorCode.REVERSE_VALIDATION_FAILURE
            ReverseErrorCode.MODEL_ASSET_MISSING, ReverseErrorCode.MODEL_LOAD_FAILURE -> AssistantExecutionErrorCode.MODEL_ERROR
            ReverseErrorCode.UNSUPPORTED_DEVICE_RUNTIME -> AssistantExecutionErrorCode.UNSUPPORTED_RUNTIME
            ReverseErrorCode.CANCELED -> AssistantExecutionErrorCode.CANCELED
            else -> AssistantExecutionErrorCode.REVERSE_SEARCH_FAILURE
        }
        return AssistantExecutionError(mapped, reverseCode = code)
    }

    private fun publish(next: AssistantExecutionState, observer: (AssistantExecutionState) -> Unit) {
        state = next
        observer(next)
    }
}

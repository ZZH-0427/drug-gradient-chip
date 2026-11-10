package com.druggradientchip.android.reverse

import java.io.Closeable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

sealed interface ReverseTaskState {
    data object Idle : ReverseTaskState
    data object Validating : ReverseTaskState
    data class Running(val progress: ReverseProgress) : ReverseTaskState
    data class Completed(val result: ReverseResult) : ReverseTaskState
    data object Canceled : ReverseTaskState
    data class Error(val code: ReverseErrorCode, val message: String) : ReverseTaskState
}

interface ActiveTaskExecutionPolicy {
    fun start()
    fun stop()
}

object NoOpTaskExecutionPolicy : ActiveTaskExecutionPolicy {
    override fun start() = Unit
    override fun stop() = Unit
}

class ReverseTaskController(
    private val runtime: ReverseRuntime,
    private val executionPolicy: ActiveTaskExecutionPolicy = NoOpTaskExecutionPolicy,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "DrugChipReverseSearch")
    },
) : Closeable {
    private val lock = Any()
    private var activeCancellation: ReverseCancellationToken? = null

    @Volatile
    var state: ReverseTaskState = ReverseTaskState.Idle
        private set

    val isRunning: Boolean
        get() = synchronized(lock) { activeCancellation != null }

    fun start(request: ReverseRequest, onState: (ReverseTaskState) -> Unit = {}): Boolean {
        synchronized(lock) {
            if (activeCancellation != null) return false
            update(ReverseTaskState.Validating, onState)
            try {
                ReverseRequestValidator.validate(request)
                executionPolicy.start()
            } catch (error: ReverseRuntimeException) {
                update(ReverseTaskState.Error(error.code, safeMessage(error)), onState)
                return true
            } catch (error: Throwable) {
                update(ReverseTaskState.Error(ReverseErrorCode.SEARCH_FAILURE, safeMessage(error)), onState)
                return true
            }
            val cancellation = ReverseCancellationToken()
            activeCancellation = cancellation
            try {
                executor.execute {
                    try {
                        val result = runtime.run(request, cancellation) { progress ->
                            update(ReverseTaskState.Running(progress), onState)
                        }
                        cancellation.throwIfCanceled()
                        update(ReverseTaskState.Completed(result), onState)
                    } catch (error: ReverseRuntimeException) {
                        if (error.code == ReverseErrorCode.CANCELED) {
                            update(ReverseTaskState.Canceled, onState)
                        } else {
                            update(ReverseTaskState.Error(error.code, safeMessage(error)), onState)
                        }
                    } catch (error: Throwable) {
                        update(ReverseTaskState.Error(ReverseErrorCode.SEARCH_FAILURE, safeMessage(error)), onState)
                    } finally {
                        synchronized(lock) { activeCancellation = null }
                        executionPolicy.stop()
                    }
                }
            } catch (error: Throwable) {
                activeCancellation = null
                executionPolicy.stop()
                update(ReverseTaskState.Error(ReverseErrorCode.SEARCH_FAILURE, safeMessage(error)), onState)
            }
            return true
        }
    }

    fun cancel(): Boolean = synchronized(lock) {
        activeCancellation?.let {
            it.cancel()
            true
        } ?: false
    }

    override fun close() {
        cancel()
        executor.shutdownNow()
    }

    private fun update(next: ReverseTaskState, observer: (ReverseTaskState) -> Unit) {
        state = next
        observer(next)
    }

    private fun safeMessage(error: Throwable): String =
        error.message?.takeIf(String::isNotBlank) ?: "Reverse prediction failed."
}

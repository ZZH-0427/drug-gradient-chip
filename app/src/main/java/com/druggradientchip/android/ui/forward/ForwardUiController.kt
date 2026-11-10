package com.druggradientchip.android.ui.forward

import com.druggradientchip.android.forward.ForwardPredictor
import com.druggradientchip.android.forward.ForwardResult
import com.druggradientchip.android.ui.UiText
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

sealed class ForwardUiState {
    data object Idle : ForwardUiState()
    data object Editing : ForwardUiState()
    data object Validating : ForwardUiState()
    data object Running : ForwardUiState()
    data class Success(val result: ForwardResult) : ForwardUiState()
    data class Error(val message: UiText) : ForwardUiState()
}

class ForwardUiController(
    private val runtime: ForwardPredictor,
    private val mapper: ForwardRequestMapper = ForwardRequestMapper(),
    private val errorMapper: ForwardErrorMapper = ForwardErrorMapper(),
    private val executor: ExecutorService = Executors.newSingleThreadExecutor(),
) {
    @Volatile
    var isRunning: Boolean = false
        private set

    fun runPrediction(
        state: ForwardFormState,
        onState: (ForwardUiState) -> Unit,
    ) {
        if (isRunning) {
            return
        }
        onState(ForwardUiState.Validating)
        val request = try {
            mapper.map(state)
        } catch (error: Throwable) {
            onState(ForwardUiState.Error(errorMapper.message(error)))
            return
        }
        isRunning = true
        onState(ForwardUiState.Running)
        executor.execute {
            try {
                onState(ForwardUiState.Success(runtime.predict(request)))
            } catch (error: Throwable) {
                onState(ForwardUiState.Error(errorMapper.message(error)))
            } finally {
                isRunning = false
            }
        }
    }

    fun close() {
        executor.shutdownNow()
    }
}

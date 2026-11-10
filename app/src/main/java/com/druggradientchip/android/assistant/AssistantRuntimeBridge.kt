package com.druggradientchip.android.assistant

import android.content.Context
import com.druggradientchip.android.forward.ForwardPredictor
import com.druggradientchip.android.forward.ForwardRequest
import com.druggradientchip.android.forward.ForwardResult
import com.druggradientchip.android.forward.ForwardRuntimeFactory
import com.druggradientchip.android.reverse.ReverseRequest
import com.druggradientchip.android.reverse.ReverseRuntimeFactory
import com.druggradientchip.android.reverse.ReverseTaskController
import com.druggradientchip.android.reverse.ReverseTaskState
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

interface AssistantRuntimeBridge {
    fun executeForward(request: ForwardRequest, onComplete: (Result<ForwardResult>) -> Unit): Boolean
    fun startReverse(request: ReverseRequest, onState: (ReverseTaskState) -> Unit): Boolean
    fun cancelReverse(): Boolean
    val reverseRunning: Boolean
}

internal class ProductAssistantRuntimeBridge(
    private val forwardPredictor: ForwardPredictor,
    private val reverseController: ReverseTaskController,
    private val forwardExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "DrugChipAssistantForward")
    },
) : AssistantRuntimeBridge {
    private val forwardLock = Any()
    private var forwardActive = false

    override fun executeForward(request: ForwardRequest, onComplete: (Result<ForwardResult>) -> Unit): Boolean {
        synchronized(forwardLock) {
            if (forwardActive) return false
            forwardActive = true
        }
        forwardExecutor.execute {
            val result = runCatching { forwardPredictor.predict(request) }
            synchronized(forwardLock) { forwardActive = false }
            onComplete(result)
        }
        return true
    }

    override fun startReverse(request: ReverseRequest, onState: (ReverseTaskState) -> Unit): Boolean =
        reverseController.start(request, onState)

    override fun cancelReverse(): Boolean = reverseController.cancel()

    override val reverseRunning: Boolean
        get() = reverseController.isRunning
}

object AssistantExecutionRuntimeFactory {
    @Volatile
    private var shared: AssistantExecutionController? = null

    fun controller(context: Context): AssistantExecutionController =
        shared ?: synchronized(this) {
            shared ?: AssistantExecutionController(
                bridge = ProductAssistantRuntimeBridge(
                    forwardPredictor = ForwardRuntimeFactory.create(context.applicationContext),
                    reverseController = ReverseRuntimeFactory.taskController(context.applicationContext),
                ),
            ).also { shared = it }
        }
}

package com.druggradientchip.android.ui.forward

import com.druggradientchip.android.forward.ForwardPredictor
import com.druggradientchip.android.forward.ForwardRequest
import com.druggradientchip.android.forward.ForwardResult
import java.util.concurrent.CountDownLatch
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForwardUiControllerTest {
    @Test
    fun runsPredictionAsynchronouslyAndReturnsSuccess() {
        val complete = CountDownLatch(1)
        val runtime = ForwardPredictor { request -> ForwardResult(request.chipCount, FloatArray(request.chipCount), "fixed-six:test") }
        val controller = ForwardUiController(runtime, executor = DirectExecutorService())
        val states = mutableListOf<ForwardUiState>()

        controller.runPrediction(ForwardFormState()) {
            states += it
            if (it is ForwardUiState.Success) complete.countDown()
        }

        assertTrue(complete.await(1, TimeUnit.SECONDS))
        assertTrue(states.any { it is ForwardUiState.Validating })
        assertTrue(states.any { it is ForwardUiState.Running })
        assertTrue(states.any { it is ForwardUiState.Success })
    }

    @Test
    fun duplicateSubmissionIsIgnoredWhileRunning() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var calls = 0
        val runtime = ForwardPredictor { request ->
            calls += 1
            entered.countDown()
            release.await(1, TimeUnit.SECONDS)
            ForwardResult(request.chipCount, FloatArray(request.chipCount), "fixed-six:test")
        }
        val controller = ForwardUiController(runtime)
        controller.runPrediction(ForwardFormState()) {}
        assertTrue(entered.await(1, TimeUnit.SECONDS))
        controller.runPrediction(ForwardFormState()) {}
        release.countDown()
        TimeUnit.MILLISECONDS.sleep(100)
        assertEquals(1, calls)
        assertFalse(controller.isRunning)
    }

    private class DirectExecutorService : AbstractExecutorService() {
        override fun execute(command: Runnable) = command.run()
        override fun shutdown() = Unit
        override fun shutdownNow(): MutableList<Runnable> = mutableListOf()
        override fun isShutdown(): Boolean = false
        override fun isTerminated(): Boolean = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = true
    }
}

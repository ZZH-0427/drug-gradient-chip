package com.druggradientchip.android.reverse

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReverseSearchAndTaskTest {
    @Test
    fun searchIsDeterministicMonotonicAndFinallyReevaluatesBestCandidate() {
        val target = floatArrayOf(.1f, .2f, .3f, .4f, .5f, .6f)
        val first = runSearch(target)
        val second = runSearch(target)

        assertEquals(first.result.evaluationCount, first.evaluatorCalls)
        assertEquals(first.result.searchConfig.evaluationBudget, ReverseSearchConfig().totalEvaluationBudget)
        assertTrue(first.progress.zipWithNext().all { it.first.fraction <= it.second.fraction })
        assertEquals(ReverseProgressState.COMPLETED, first.progress.last().state)
        assertEquals(1.0, first.progress.last().fraction, 0.0)
        assertEquals(3, first.restartCount)
        assertEquals(6, first.result.candidate.chips.size)
        assertEquals((1..6).toList(), first.result.candidate.chips.map { it.position })
        assertTrue(first.result.candidate.chips.all { it.leftDistanceMm in DISTANCES && it.rightDistanceMm in DISTANCES })
        assertEquals(first.result.candidate, second.result.candidate)
        assertArrayEquals(first.result.recomputedConcentrations, second.result.recomputedConcentrations, 0.0f)
    }

    @Test
    fun cancellationIsCooperative() {
        val cancellation = ReverseCancellationToken()
        var calls = 0
        val engine = ReverseSearchEngine(ReverseEvaluator { _, _ ->
            calls += 1
            if (calls == 10) cancellation.cancel()
            FloatArray(6) { .3f }
        })

        val error = runCatching {
            engine.optimize(request(), cancellation) {}
        }.exceptionOrNull() as ReverseRuntimeException

        assertEquals(ReverseErrorCode.CANCELED, error.code)
        assertTrue(calls <= 10)
    }

    @Test
    fun controllerAllowsOneTaskAndRemainsReusableAfterCancellation() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val terminal = CountDownLatch(1)
        val policy = RecordingPolicy()
        val runtime = ReverseRuntime { _, cancellation, progress ->
            entered.countDown()
            while (!release.await(5, TimeUnit.MILLISECONDS)) cancellation.throwIfCanceled()
            cancellation.throwIfCanceled()
            progress(completedProgress())
            result()
        }
        val controller = ReverseTaskController(runtime, policy)

        assertTrue(controller.start(request()) { if (it is ReverseTaskState.Canceled) terminal.countDown() })
        assertTrue(entered.await(1, TimeUnit.SECONDS))
        assertFalse(controller.start(request()))
        assertTrue(controller.cancel())
        assertTrue(terminal.await(1, TimeUnit.SECONDS))
        assertFalse(controller.isRunning)
        assertEquals(1, policy.starts)
        assertEquals(1, policy.stops)

        val completed = CountDownLatch(1)
        release.countDown()
        assertTrue(controller.start(request()) { if (it is ReverseTaskState.Completed) completed.countDown() })
        assertTrue(completed.await(1, TimeUnit.SECONDS))
        assertEquals(2, policy.starts)
        assertEquals(2, policy.stops)
        controller.close()
    }

    @Test
    fun invalidRequestNeverStartsExecutionPolicy() {
        val policy = RecordingPolicy()
        val controller = ReverseTaskController(ReverseRuntime { _, _, _ -> error("must not run") }, policy)
        val states = mutableListOf<ReverseTaskState>()

        assertTrue(controller.start(ReverseRequest(HighConcentrationInlet.INLET_1, FloatArray(5)), states::add))

        assertTrue(states.last() is ReverseTaskState.Error)
        assertEquals(0, policy.starts)
        assertFalse(controller.isRunning)
        controller.close()
    }

    @Test
    fun executorRejectionStopsExecutionPolicy() {
        val policy = RecordingPolicy()
        val executor = Executors.newSingleThreadExecutor().apply { shutdownNow() }
        val controller = ReverseTaskController(ReverseRuntime { _, _, _ -> result() }, policy, executor)
        val states = mutableListOf<ReverseTaskState>()

        assertTrue(controller.start(request(), states::add))

        assertTrue(states.last() is ReverseTaskState.Error)
        assertFalse(controller.isRunning)
        assertEquals(1, policy.starts)
        assertEquals(1, policy.stops)
    }

    private fun runSearch(target: FloatArray): SearchRun {
        var calls = 0
        val evaluator = ReverseEvaluator { candidate, inlet ->
            calls += 1
            FloatArray(6) { chip ->
                val left = candidate.widths[chip * 2]
                val right = candidate.widths[chip * 2 + 1]
                val ratio = left / (left + right)
                if (inlet == HighConcentrationInlet.INLET_1) ratio else 1.0f - ratio
            }
        }
        val progress = mutableListOf<ReverseProgress>()
        val outcome = ReverseSearchEngine(evaluator).optimize(
            ReverseRequest(HighConcentrationInlet.INLET_1, target),
            ReverseCancellationToken(),
            progress::add,
        )
        return SearchRun(outcome.result, progress, calls, outcome.restartCandidates.size)
    }

    private fun request() = ReverseRequest(
        HighConcentrationInlet.INLET_1,
        floatArrayOf(.1f, .2f, .3f, .4f, .5f, .6f),
    )

    private fun completedProgress() = ReverseProgress(1, 1, 1.0, 0, ReverseProgressState.COMPLETED)

    private fun result() = ReverseResult(
        HighConcentrationInlet.INLET_1,
        6,
        ReverseCandidate(emptyList()),
        FloatArray(6),
        FloatArray(6),
        ReverseMetrics(0.0, 0.0, 0.0, 1.0, true),
        1,
        1.0,
        ReverseSearchConfig().snapshot(),
        emptyList(),
        FixedSixReverseEvaluator.MODEL_IDENTIFIER,
    )

    private data class SearchRun(
        val result: ReverseResult,
        val progress: List<ReverseProgress>,
        val evaluatorCalls: Int,
        val restartCount: Int,
    )

    private class RecordingPolicy : ActiveTaskExecutionPolicy {
        var starts = 0
        var stops = 0
        override fun start() { starts += 1 }
        override fun stop() { stops += 1 }
    }

    companion object {
        private val DISTANCES = setOf(7.0f, 13.0f, 19.0f, 25.0f, 31.0f, 37.0f)
    }
}

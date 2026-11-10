package com.druggradientchip.android.reverse

import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReverseContractsAndMetricsTest {
    @Test
    fun validatesFixedSixRequestAndBinaryInletMapping() {
        val request = ReverseRequest(HighConcentrationInlet.INLET_1, FloatArray(6) { it / 10.0f })
        ReverseRequestValidator.validate(request)

        assertEquals(HighConcentrationInlet.INLET_1, ReverseRequestValidator.highInlet(1.0f, 0.0f))
        assertEquals(HighConcentrationInlet.INLET_2, ReverseRequestValidator.highInlet(0.0f, 1.0f))
        assertEquals(1.0f, request.selectedHighInlet.inlet1)
        assertEquals(0.0f, request.selectedHighInlet.inlet2)
    }

    @Test
    fun rejectsWrongCountNonFiniteTargetsAndInvalidInlet() {
        assertCode(ReverseErrorCode.INVALID_TARGET) {
            ReverseRequestValidator.validate(ReverseRequest(HighConcentrationInlet.INLET_1, FloatArray(5)))
        }
        assertCode(ReverseErrorCode.INVALID_TARGET) {
            ReverseRequestValidator.validate(
                ReverseRequest(HighConcentrationInlet.INLET_1, floatArrayOf(0f, 0f, Float.NaN, 0f, 0f, 0f)),
            )
        }
        assertCode(ReverseErrorCode.INVALID_INLET) { ReverseRequestValidator.highInlet(0.5f, 0.5f) }
    }

    @Test
    fun metricsAndObjectiveMatchReferenceIncludingTies() {
        val predicted = floatArrayOf(.11f, .19f, .31f, .39f, .52f, .58f)
        val target = floatArrayOf(.10f, .20f, .30f, .40f, .50f, .60f)
        val metrics = ReverseMetricsCalculator.calculate(predicted, target)

        assertEquals(0.08 / 6.0, metrics.mae, 1.0e-8)
        assertEquals(sqrt(0.0002), metrics.rmse, 1.0e-8)
        assertEquals(0.02, metrics.maxAe, 1.0e-7)
        assertEquals(1.0, metrics.spearman, 0.0)
        assertTrue(metrics.qualityPassed)
        assertEquals(0.0002 + 0.10 * 0.08 / 6.0, ReverseMetricsCalculator.objective(predicted, target), 1.0e-8)

        val ties = ReverseMetricsCalculator.spearman(
            floatArrayOf(1f, 2f, 2f, 4f, 5f, 6f),
            floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f),
        )
        assertEquals(17.0 / sqrt(297.5), ties, 1.0e-12)
    }

    @Test
    fun qualityGateIsReportedWithoutRejectingResult() {
        val metrics = ReverseMetricsCalculator.calculate(FloatArray(6), FloatArray(6) { 1.0f })
        assertFalse(metrics.qualityPassed)
    }

    @Test
    fun candidateRejectsInvalidWidthsAndBends() {
        SearchCandidate(FloatArray(12) { .2f }, IntArray(12) { 3 }).validate()
        assertCode(ReverseErrorCode.INVALID_REQUEST) {
            SearchCandidate(FloatArray(12) { if (it == 0) .09f else .2f }, IntArray(12) { 3 }).validate()
        }
        assertCode(ReverseErrorCode.INVALID_REQUEST) {
            SearchCandidate(FloatArray(12) { .2f }, IntArray(12) { if (it == 0) 7 else 3 }).validate()
        }
    }

    private fun assertCode(expected: ReverseErrorCode, action: () -> Unit) {
        val error = runCatching(action).exceptionOrNull() as ReverseRuntimeException
        assertEquals(expected, error.code)
    }
}

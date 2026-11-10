package com.druggradientchip.android.reverse

import com.druggradientchip.android.forward.ForwardErrorCode
import com.druggradientchip.android.forward.ForwardPredictor
import com.druggradientchip.android.forward.ForwardResult
import com.druggradientchip.android.forward.ForwardRuntimeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FixedSixReverseEvaluatorTest {
    @Test
    fun mapsCandidateThroughProductionFixedSixForwardContract() {
        var captured: com.druggradientchip.android.forward.ForwardRequest? = null
        val evaluator = FixedSixReverseEvaluator(ForwardPredictor { request ->
            captured = request
            ForwardResult(6, floatArrayOf(.1f, .2f, .3f, .4f, .5f, .6f), FixedSixReverseEvaluator.MODEL_IDENTIFIER)
        })
        val candidate = SearchCandidate(
            FloatArray(12) { .1f + it * .01f },
            intArrayOf(1, 6, 2, 5, 3, 4, 4, 3, 5, 2, 6, 1),
        )

        val output = evaluator.predict(candidate, HighConcentrationInlet.INLET_2)

        assertEquals(6, output.size)
        assertEquals(6, captured!!.chipCount)
        assertEquals(0.0f, captured!!.inlet1)
        assertEquals(1.0f, captured!!.inlet2)
        assertEquals("1to6", captured!!.chips.first().chipType)
        assertEquals(7.0f, captured!!.chips.first().leftBendStraightDistanceMm)
        assertEquals(37.0f, captured!!.chips.first().rightBendStraightDistanceMm)
        assertEquals(1, evaluator.evaluationCount)
        assertTrue(evaluator.firstEvaluationMs != null)
    }

    @Test
    fun mapsForwardFailuresToReverseErrorCodes() {
        val evaluator = FixedSixReverseEvaluator(ForwardPredictor {
            throw ForwardRuntimeException(ForwardErrorCode.MISSING_MODEL_ASSET, "missing")
        })
        val error = runCatching {
            evaluator.predict(SearchCandidate(FloatArray(12) { .2f }, IntArray(12) { 3 }), HighConcentrationInlet.INLET_1)
        }.exceptionOrNull() as ReverseRuntimeException

        assertEquals(ReverseErrorCode.MODEL_ASSET_MISSING, error.code)
    }
}

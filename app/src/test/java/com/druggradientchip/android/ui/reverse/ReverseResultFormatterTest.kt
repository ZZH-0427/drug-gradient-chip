package com.druggradientchip.android.ui.reverse

import com.druggradientchip.android.R
import com.druggradientchip.android.reverse.HighConcentrationInlet
import com.druggradientchip.android.reverse.ReverseCandidate
import com.druggradientchip.android.reverse.ReverseChipCandidate
import com.druggradientchip.android.reverse.ReverseErrorCode
import com.druggradientchip.android.reverse.ReverseMetrics
import com.druggradientchip.android.reverse.ReverseResult
import com.druggradientchip.android.reverse.ReverseSearchConfigSnapshot
import com.druggradientchip.android.ui.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReverseResultFormatterTest {
    private val formatter = ReverseResultFormatter()

    @Test
    fun formatsInletMetricsQualityAndSixStructuredChipRows() {
        val result = result(qualityPassed = true)
        val rows = formatter.chips(result, floatArrayOf(.08f, .14f, .25f, .38f, .48f, .58f))

        assertEquals(UiText.Resource(R.string.reverse_inlet2_value), formatter.inlet(result.selectedHighInlet))
        assertEquals(UiText.Resource(R.string.quality_passed), formatter.quality(result))
        assertTrue(formatter.metrics(result).contains("Spearman  1.0000"))
        assertEquals(6, rows.size)
        assertEquals(UiText.Resource(R.string.reverse_chip_title, listOf(6)), rows.last().title)
        assertEquals(UiText.Resource(R.string.reverse_chip_structure, listOf("5to4")), rows.last().structure)
        assertEquals(R.string.reverse_chip_geometry, (rows.last().geometry as UiText.Resource).id)
        assertEquals(R.string.reverse_chip_concentrations, (rows.first().concentrations as UiText.Resource).id)
    }

    @Test
    fun formatsQualityWarningWithoutHidingMetrics() {
        val result = result(qualityPassed = false)
        assertEquals(UiText.Resource(R.string.quality_warning), formatter.quality(result))
        assertTrue(formatter.metrics(result).contains("MAE"))
        assertTrue(formatter.metrics(result).contains("MaxAE"))
    }

    @Test
    fun mapsTypedRuntimeErrorsToReadableMessages() {
        val mapper = ReverseErrorMapper()
        assertEquals(UiText.Resource(R.string.reverse_error_model_missing), mapper.message(ReverseErrorCode.MODEL_ASSET_MISSING))
        assertEquals(
            UiText.Resource(R.string.reverse_error_runtime_unsupported),
            mapper.message(ReverseErrorCode.UNSUPPORTED_DEVICE_RUNTIME),
        )
        assertEquals(UiText.Resource(R.string.reverse_error_canceled), mapper.message(ReverseErrorCode.CANCELED))
    }

    private fun result(qualityPassed: Boolean): ReverseResult {
        val chips = (1..6).map { position ->
            ReverseChipCandidate(position, "5to4", .2f, .21f, 5, 4, 31f, 25f)
        }
        return ReverseResult(
            selectedHighInlet = HighConcentrationInlet.INLET_2,
            chipCount = 6,
            candidate = ReverseCandidate(chips),
            recomputedConcentrations = floatArrayOf(.081f, .141f, .251f, .381f, .481f, .581f),
            perPositionErrors = FloatArray(6) { .001f },
            metrics = ReverseMetrics(.001, .001, .001, 1.0, qualityPassed),
            evaluationCount = 796,
            runtimeMs = 8_600.0,
            searchConfig = ReverseSearchConfigSnapshot(3, .1f, .3f, 1, 6, listOf(.04f), .1, 42, true, 796),
            warnings = emptyList(),
            modelIdentifier = "fixed-six:tensor-only",
        )
    }
}

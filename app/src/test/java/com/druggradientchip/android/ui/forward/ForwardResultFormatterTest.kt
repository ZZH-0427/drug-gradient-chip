package com.druggradientchip.android.ui.forward

import com.druggradientchip.android.R
import com.druggradientchip.android.forward.ForwardResult
import com.druggradientchip.android.ui.UiText
import org.junit.Assert.assertEquals
import org.junit.Test

class ForwardResultFormatterTest {
    private val formatter = ForwardResultFormatter()

    @Test
    fun formatsOneRowPerN4Output() {
        val rows = formatter.resultRows(ForwardResult(4, floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f), "dynamic-zero-shot:n4"))
        assertEquals(4, rows.size)
        assertEquals(ForwardResultRow(4, "0.400000"), rows.last())
    }

    @Test
    fun formatsOneRowPerN10Output() {
        val rows = formatter.resultRows(ForwardResult(10, FloatArray(10) { it / 10.0f }, "dynamic-zero-shot:n10"))
        assertEquals(10, rows.size)
        assertEquals(ForwardResultRow(10, "0.900000"), rows.last())
    }

    @Test
    fun mapsModelIdentifierToReadableLabel() {
        assertEquals(UiText.Resource(R.string.forward_model_fixed_six), formatter.modelLabel("fixed-six:fixed_six_tensor_only.pte"))
        assertEquals(
            UiText.Resource(R.string.forward_model_dynamic_zero_shot),
            formatter.modelLabel("dynamic-zero-shot:dynamic_forward_n10.pte"),
        )
    }
}

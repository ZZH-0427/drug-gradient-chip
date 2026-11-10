package com.druggradientchip.android.ui.reverse

import com.druggradientchip.android.R
import com.druggradientchip.android.reverse.HighConcentrationInlet
import com.druggradientchip.android.ui.UiText
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReverseRequestMapperTest {
    private val mapper = ReverseRequestMapper()

    @Test
    fun mapsSixTargetsAndSelectedHighInlet() {
        val request = mapper.map(
            ReverseFormState(
                selectedHighInlet = HighConcentrationInlet.INLET_2,
                targets = listOf(".08", "0.14", "0.25", "0.38", "0.48", "0.58"),
            ),
        )

        assertEquals(HighConcentrationInlet.INLET_2, request.selectedHighInlet)
        assertArrayEquals(floatArrayOf(.08f, .14f, .25f, .38f, .48f, .58f), request.targetConcentrations, 0f)
    }

    @Test
    fun rejectsBlankAndNonFiniteTargets() {
        listOf("", "NaN", "Infinity", "-Infinity").forEach { invalid ->
            val targets = ReverseFormState.DEFAULT_TARGETS.toMutableList().also { it[2] = invalid }
            val error = runCatching { mapper.map(ReverseFormState(targets = targets)) }.exceptionOrNull()
            assertTrue(error is ReverseFormException)
            assertEquals(UiText.Resource(R.string.reverse_error_target_value, listOf(3)), (error as ReverseFormException).uiText)
        }
    }

    @Test
    fun rejectsAnyTargetCountOtherThanSix() {
        val error = runCatching { mapper.map(ReverseFormState(targets = List(5) { "0.2" })) }.exceptionOrNull()
        assertTrue(error is ReverseFormException)
    }
}

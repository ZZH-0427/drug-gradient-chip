package com.druggradientchip.android.ui

import com.druggradientchip.android.i18n.AppLanguage
import com.druggradientchip.android.reverse.HighConcentrationInlet
import com.druggradientchip.android.ui.forward.ForwardFormState
import com.druggradientchip.android.ui.forward.ForwardRequestMapper
import com.druggradientchip.android.ui.reverse.ReverseFormState
import com.druggradientchip.android.ui.reverse.ReverseRequestMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalizationContractTest {
    @Test
    fun forwardStructuredRequestIsInvariantAcrossLanguageModes() {
        val form = ForwardFormState().withChipCount(10)
        val requests = AppLanguage.values().map { ForwardRequestMapper().map(form) }

        requests.drop(1).forEach { assertEquals(requests.first(), it) }
    }

    @Test
    fun reverseStructuredRequestIsInvariantAcrossLanguageModes() {
        val form = ReverseFormState(
            selectedHighInlet = HighConcentrationInlet.INLET_2,
            targets = listOf(".08", "0.14", "0.25", "0.38", "0.48", "0.58"),
        )
        val requests = AppLanguage.values().map { ReverseRequestMapper().map(form) }

        requests.drop(1).forEach { request ->
            assertEquals(requests.first().selectedHighInlet, request.selectedHighInlet)
            assertTrue(requests.first().targetConcentrations.contentEquals(request.targetConcentrations))
        }
    }
}

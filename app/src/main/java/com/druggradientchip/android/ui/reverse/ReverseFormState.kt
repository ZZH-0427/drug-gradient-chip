package com.druggradientchip.android.ui.reverse

import com.druggradientchip.android.reverse.HighConcentrationInlet

data class ReverseFormState(
    val selectedHighInlet: HighConcentrationInlet = HighConcentrationInlet.INLET_1,
    val targets: List<String> = DEFAULT_TARGETS,
) {
    companion object {
        val DEFAULT_TARGETS = listOf("0.10", "0.25", "0.40", "0.55", "0.70", "0.85")
    }
}

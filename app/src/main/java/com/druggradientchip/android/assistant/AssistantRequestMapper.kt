package com.druggradientchip.android.assistant

import com.druggradientchip.android.forward.ForwardChipInput
import com.druggradientchip.android.forward.ForwardRequest
import com.druggradientchip.android.forward.ForwardValidation
import com.druggradientchip.android.reverse.ReverseRequest
import com.druggradientchip.android.reverse.ReverseRequestValidator

class AssistantRequestMapper {
    fun forward(draft: ForwardDraft): ForwardRequest {
        val chipCount = requireNotNull(draft.chipCount) { "Forward chip count is missing." }
        val inlet = requireNotNull(draft.highInlet) { "Forward high inlet is missing." }
        val request = ForwardRequest(
            sampleName = ASSISTANT_SAMPLE_NAME,
            chipCount = chipCount,
            inlet1 = inlet.inlet1,
            inlet2 = inlet.inlet2,
            chips = (1..chipCount).map { position ->
                val chip = requireNotNull(draft.chips[position]) { "Forward chip $position is missing." }
                ForwardChipInput(
                    position = position,
                    chipType = requireNotNull(chip.chipType) { "Forward chip $position type is missing." },
                    leftWidthMm = requireNotNull(chip.leftWidthMm) { "Forward chip $position left width is missing." },
                    rightWidthMm = requireNotNull(chip.rightWidthMm) { "Forward chip $position right width is missing." },
                    leftBendStraightDistanceMm = requireNotNull(chip.leftDistanceMm) { "Forward chip $position left distance is missing." },
                    rightBendStraightDistanceMm = requireNotNull(chip.rightDistanceMm) { "Forward chip $position right distance is missing." },
                )
            },
        )
        ForwardValidation.validate(request)
        return request
    }

    fun reverse(draft: ReverseDraft): ReverseRequest {
        val inlet = requireNotNull(draft.highInlet) { "Reverse high inlet is missing." }
        return ReverseRequest(inlet, draft.targetConcentrations.toFloatArray())
            .also(ReverseRequestValidator::validate)
    }

    companion object {
        const val ASSISTANT_SAMPLE_NAME = "Android Assistant"
    }
}

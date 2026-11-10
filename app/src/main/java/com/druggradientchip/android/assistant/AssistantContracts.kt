package com.druggradientchip.android.assistant

import com.druggradientchip.android.reverse.HighConcentrationInlet

enum class AssistantMode { FORWARD, REVERSE }

enum class DraftStatus { INCOMPLETE, READY_FOR_CONFIRMATION, INVALID }

enum class ChipField {
    CHIP_TYPE,
    LEFT_WIDTH,
    RIGHT_WIDTH,
    LEFT_DISTANCE,
    RIGHT_DISTANCE,
}

enum class MissingFieldCode {
    MODE,
    CHIP_COUNT,
    HIGH_INLET,
    CHIP_TYPE,
    LEFT_WIDTH,
    RIGHT_WIDTH,
    LEFT_DISTANCE,
    RIGHT_DISTANCE,
    TARGET_CONCENTRATIONS,
}

data class MissingField(val code: MissingFieldCode, val chipPosition: Int? = null)

enum class ClarificationReason {
    EMPTY_MESSAGE,
    UNSUPPORTED_OR_AMBIGUOUS,
    CONFLICTING_MODE,
    CONFLICTING_INLET,
    CHIP_POSITION_REQUIRED,
    CONFLICTING_CHIP_POSITIONS,
    REPEATED_FIELD_CONFLICT,
    MALFORMED_NUMBER,
}

enum class ValidationCode {
    UNSUPPORTED_CHIP_COUNT,
    CHIP_POSITION_OUT_OF_RANGE,
    INVALID_CHIP_TYPE,
    INVALID_WIDTH,
    INVALID_DISTANCE,
    REVERSE_REQUIRES_SIX_TARGETS,
    NON_FINITE_TARGET,
}

data class ValidationIssue(
    val code: ValidationCode,
    val chipPosition: Int? = null,
    val field: ChipField? = null,
)

enum class ParseWarning { UNRECOGNIZED_TEXT }

enum class HelpCategory { OVERVIEW, FORWARD, REVERSE }

enum class HelpExample {
    FORWARD_GLOBAL_PARAMETERS,
    FORWARD_CHIP_PARAMETERS,
    FORWARD_CONTEXT_CONTINUATION,
    REVERSE_TARGETS_AND_INLET,
}

data class ChipPatch(
    val position: Int,
    val chipType: String? = null,
    val leftWidthMm: Float? = null,
    val rightWidthMm: Float? = null,
    val leftDistanceMm: Float? = null,
    val rightDistanceMm: Float? = null,
)

sealed interface AssistantCommand {
    data class ForwardUpdate(
        val chipCount: Int? = null,
        val highInlet: HighConcentrationInlet? = null,
        val chips: List<ChipPatch> = emptyList(),
    ) : AssistantCommand

    data class ReverseUpdate(
        val highInlet: HighConcentrationInlet? = null,
        val targetConcentrations: List<Float>? = null,
    ) : AssistantCommand

    data object ConfirmAndRun : AssistantCommand
    data object Cancel : AssistantCommand
    data object Reset : AssistantCommand
    data class Help(val category: HelpCategory, val examples: List<HelpExample>) : AssistantCommand
    data class Clarify(val reason: ClarificationReason) : AssistantCommand
}

data class ParseResult(
    val command: AssistantCommand,
    val warnings: List<ParseWarning> = emptyList(),
)

data class ForwardChipDraft(
    val chipType: String? = null,
    val leftWidthMm: Float? = null,
    val rightWidthMm: Float? = null,
    val leftDistanceMm: Float? = null,
    val rightDistanceMm: Float? = null,
) {
    fun apply(patch: ChipPatch) = copy(
        chipType = patch.chipType ?: chipType,
        leftWidthMm = patch.leftWidthMm ?: leftWidthMm,
        rightWidthMm = patch.rightWidthMm ?: rightWidthMm,
        leftDistanceMm = patch.leftDistanceMm ?: leftDistanceMm,
        rightDistanceMm = patch.rightDistanceMm ?: rightDistanceMm,
    )
}

data class ForwardDraft(
    val chipCount: Int? = null,
    val highInlet: HighConcentrationInlet? = null,
    val chips: Map<Int, ForwardChipDraft> = emptyMap(),
)

data class ReverseDraft(
    val highInlet: HighConcentrationInlet? = null,
    val targetConcentrations: List<Float> = emptyList(),
)

data class AssistantSessionState(
    val activeMode: AssistantMode? = null,
    val activeChipPosition: Int? = null,
    val forwardDraft: ForwardDraft = ForwardDraft(),
    val reverseDraft: ReverseDraft = ReverseDraft(),
    val pendingAmbiguity: ClarificationReason? = null,
    val missingFields: List<MissingField> = listOf(MissingField(MissingFieldCode.MODE)),
    val confirmationState: DraftStatus = DraftStatus.INCOMPLETE,
    val lastParseWarnings: List<ParseWarning> = emptyList(),
)

data class SessionUpdateResult(
    val command: AssistantCommand,
    val state: AssistantSessionState,
    val validationIssues: List<ValidationIssue> = emptyList(),
)

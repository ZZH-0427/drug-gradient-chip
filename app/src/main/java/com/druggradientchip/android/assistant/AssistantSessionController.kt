package com.druggradientchip.android.assistant

import com.druggradientchip.android.forward.ForwardValidation

class AssistantSessionController(private val interpreter: AssistantInterpreter = AssistantInterpreter()) {
    fun process(state: AssistantSessionState, message: String): SessionUpdateResult {
        val parsed = interpreter.parse(message, state)
        val command = parsed.command
        if (command is AssistantCommand.Clarify) {
            return SessionUpdateResult(
                command,
                state.copy(
                    pendingAmbiguity = command.reason,
                    confirmationState = DraftStatus.INVALID,
                    lastParseWarnings = parsed.warnings,
                ),
            )
        }
        if (command is AssistantCommand.Reset) {
            return SessionUpdateResult(command, AssistantSessionState())
        }
        if (command is AssistantCommand.Cancel || command is AssistantCommand.Help || command is AssistantCommand.ConfirmAndRun) {
            return SessionUpdateResult(command, evaluate(state.copy(pendingAmbiguity = null, lastParseWarnings = parsed.warnings)))
        }

        val issues = validate(command, state)
        if (issues.isNotEmpty()) {
            return SessionUpdateResult(
                command,
                state.copy(
                    pendingAmbiguity = null,
                    confirmationState = DraftStatus.INVALID,
                    lastParseWarnings = parsed.warnings,
                ),
                issues,
            )
        }

        val updated = when (command) {
            is AssistantCommand.ForwardUpdate -> applyForward(state, command)
            is AssistantCommand.ReverseUpdate -> applyReverse(state, command)
            else -> state
        }
        return SessionUpdateResult(command, evaluate(updated.copy(pendingAmbiguity = null, lastParseWarnings = parsed.warnings)))
    }

    private fun applyForward(state: AssistantSessionState, command: AssistantCommand.ForwardUpdate): AssistantSessionState {
        val nextCount = command.chipCount ?: state.forwardDraft.chipCount
        val chips = state.forwardDraft.chips.toMutableMap()
        command.chips.forEach { patch -> chips[patch.position] = (chips[patch.position] ?: ForwardChipDraft()).apply(patch) }
        if (command.chipCount != null) chips.keys.filter { it > command.chipCount }.forEach(chips::remove)
        return state.copy(
            activeMode = AssistantMode.FORWARD,
            activeChipPosition = command.chips.lastOrNull()?.position ?: state.activeChipPosition,
            forwardDraft = ForwardDraft(nextCount, command.highInlet ?: state.forwardDraft.highInlet, chips.toMap()),
        )
    }

    private fun applyReverse(state: AssistantSessionState, command: AssistantCommand.ReverseUpdate) = state.copy(
        activeMode = AssistantMode.REVERSE,
        activeChipPosition = null,
        reverseDraft = ReverseDraft(
            command.highInlet ?: state.reverseDraft.highInlet,
            command.targetConcentrations ?: state.reverseDraft.targetConcentrations,
        ),
    )

    private fun validate(command: AssistantCommand, state: AssistantSessionState): List<ValidationIssue> = when (command) {
        is AssistantCommand.ForwardUpdate -> validateForward(command, state)
        is AssistantCommand.ReverseUpdate -> validateReverse(command)
        else -> emptyList()
    }

    private fun validateForward(command: AssistantCommand.ForwardUpdate, state: AssistantSessionState): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val count = command.chipCount ?: state.forwardDraft.chipCount
        if (command.chipCount != null && command.chipCount !in ForwardValidation.MIN_CHIP_COUNT..ForwardValidation.MAX_CHIP_COUNT) {
            issues += ValidationIssue(ValidationCode.UNSUPPORTED_CHIP_COUNT)
        }
        command.chips.forEach { patch ->
            if (patch.position !in 1..(count ?: ForwardValidation.MAX_CHIP_COUNT)) {
                issues += ValidationIssue(ValidationCode.CHIP_POSITION_OUT_OF_RANGE, patch.position)
            }
            patch.chipType?.let {
                if (!Regex("[1-6]to[1-6]").matches(it)) issues += ValidationIssue(ValidationCode.INVALID_CHIP_TYPE, patch.position, ChipField.CHIP_TYPE)
            }
            listOf(ChipField.LEFT_WIDTH to patch.leftWidthMm, ChipField.RIGHT_WIDTH to patch.rightWidthMm).forEach { (field, value) ->
                if (value != null && (!value.isFinite() || value < ForwardValidation.MIN_WIDTH_MM || value > ForwardValidation.MAX_WIDTH_MM)) {
                    issues += ValidationIssue(ValidationCode.INVALID_WIDTH, patch.position, field)
                }
            }
            listOf(ChipField.LEFT_DISTANCE to patch.leftDistanceMm, ChipField.RIGHT_DISTANCE to patch.rightDistanceMm).forEach { (field, value) ->
                if (value != null && (!value.isFinite() || value <= 0f)) issues += ValidationIssue(ValidationCode.INVALID_DISTANCE, patch.position, field)
            }
        }
        return issues
    }

    private fun validateReverse(command: AssistantCommand.ReverseUpdate): List<ValidationIssue> {
        val targets = command.targetConcentrations ?: return emptyList()
        if (targets.size != 6) return listOf(ValidationIssue(ValidationCode.REVERSE_REQUIRES_SIX_TARGETS))
        if (targets.any { !it.isFinite() }) return listOf(ValidationIssue(ValidationCode.NON_FINITE_TARGET))
        return emptyList()
    }

    private fun evaluate(state: AssistantSessionState): AssistantSessionState {
        val missing = when (state.activeMode) {
            AssistantMode.FORWARD -> forwardMissing(state.forwardDraft)
            AssistantMode.REVERSE -> reverseMissing(state.reverseDraft)
            null -> listOf(MissingField(MissingFieldCode.MODE))
        }
        return state.copy(
            missingFields = missing,
            confirmationState = if (missing.isEmpty()) DraftStatus.READY_FOR_CONFIRMATION else DraftStatus.INCOMPLETE,
        )
    }

    private fun forwardMissing(draft: ForwardDraft): List<MissingField> {
        val missing = mutableListOf<MissingField>()
        if (draft.chipCount == null) missing += MissingField(MissingFieldCode.CHIP_COUNT)
        if (draft.highInlet == null) missing += MissingField(MissingFieldCode.HIGH_INLET)
        val count = draft.chipCount ?: return missing
        (1..count).forEach { position ->
            val chip = draft.chips[position]
            if (chip?.chipType == null) missing += MissingField(MissingFieldCode.CHIP_TYPE, position)
            if (chip?.leftWidthMm == null) missing += MissingField(MissingFieldCode.LEFT_WIDTH, position)
            if (chip?.rightWidthMm == null) missing += MissingField(MissingFieldCode.RIGHT_WIDTH, position)
            if (chip?.leftDistanceMm == null) missing += MissingField(MissingFieldCode.LEFT_DISTANCE, position)
            if (chip?.rightDistanceMm == null) missing += MissingField(MissingFieldCode.RIGHT_DISTANCE, position)
        }
        return missing
    }

    private fun reverseMissing(draft: ReverseDraft): List<MissingField> = buildList {
        if (draft.highInlet == null) add(MissingField(MissingFieldCode.HIGH_INLET))
        if (draft.targetConcentrations.size != 6) add(MissingField(MissingFieldCode.TARGET_CONCENTRATIONS))
    }
}

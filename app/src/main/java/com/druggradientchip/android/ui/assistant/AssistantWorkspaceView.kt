package com.druggradientchip.android.ui.assistant

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.druggradientchip.android.R
import com.druggradientchip.android.assistant.AssistantCommand
import com.druggradientchip.android.assistant.AssistantExecutionResult
import com.druggradientchip.android.assistant.AssistantExecutionState
import com.druggradientchip.android.assistant.AssistantExecutionStatus
import com.druggradientchip.android.assistant.AssistantMode
import com.druggradientchip.android.assistant.DraftStatus
import com.druggradientchip.android.reverse.HighConcentrationInlet
import com.druggradientchip.android.ui.UiText
import com.druggradientchip.android.ui.forward.ForwardResultFormatter
import java.util.Locale
import kotlin.math.roundToInt

class AssistantWorkspaceView(
    private val activity: Activity,
    root: View,
    private val mapper: AssistantPresentationMapper = AssistantPresentationMapper(),
) {
    private val historyScroll: ScrollView = root.findViewById(R.id.assistantHistoryScroll)
    private val historyContainer: LinearLayout = root.findViewById(R.id.assistantHistoryContainer)
    private val commandInput: EditText = root.findViewById(R.id.assistantCommandInput)
    private val sendButton: Button = root.findViewById(R.id.assistantSendButton)
    private val helpButton: Button = root.findViewById(R.id.assistantHelpButton)
    private val resetButton: Button = root.findViewById(R.id.assistantResetButton)
    private val cancelButton: Button = root.findViewById(R.id.assistantCancelButton)
    private val confirmButton: Button = root.findViewById(R.id.assistantConfirmButton)
    private val continueButton: Button = root.findViewById(R.id.assistantContinueButton)
    private val statusText: TextView = root.findViewById(R.id.assistantStatusText)
    private val modeText: TextView = root.findViewById(R.id.assistantModeText)
    private val activeChipText: TextView = root.findViewById(R.id.assistantActiveChipText)
    private val draftContainer: LinearLayout = root.findViewById(R.id.assistantDraftContainer)
    private val missingText: TextView = root.findViewById(R.id.assistantMissingText)
    private val staleText: TextView = root.findViewById(R.id.assistantStaleText)
    private val progressGroup: View = root.findViewById(R.id.assistantProgressGroup)
    private val progressBar: ProgressBar = root.findViewById(R.id.assistantProgressBar)
    private val progressText: TextView = root.findViewById(R.id.assistantProgressText)
    private val resultContainer: LinearLayout = root.findViewById(R.id.assistantResultContainer)
    private val forwardFormatter = ForwardResultFormatter()

    var onSend: ((String) -> Unit)? = null
    var onConfirm: (() -> Unit)? = null
    var onCancel: (() -> Unit)? = null
    var onReset: (() -> Unit)? = null
    var onHelp: (() -> Unit)? = null

    init {
        commandInput.setText(AssistantConversationStore.inputText)
        commandInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                send()
                true
            } else false
        }
        sendButton.setOnClickListener { send() }
        confirmButton.setOnClickListener { onConfirm?.invoke() }
        cancelButton.setOnClickListener { onCancel?.invoke() }
        resetButton.setOnClickListener { onReset?.invoke() }
        helpButton.setOnClickListener { onHelp?.invoke() }
        continueButton.setOnClickListener { commandInput.requestFocus() }
        renderHistory()
    }

    fun saveInput() {
        AssistantConversationStore.inputText = commandInput.text.toString()
    }

    fun clearAcceptedInput() {
        commandInput.text.clear()
        AssistantConversationStore.inputText = ""
        dismissKeyboard()
    }

    fun recordUser(message: String) {
        AssistantConversationStore.add(AssistantHistoryEntry(AssistantHistoryRole.USER, UiText.Technical(message)))
        renderHistory()
    }

    fun resetHistory() {
        AssistantConversationStore.clear()
        AssistantConversationStore.add(
            AssistantHistoryEntry(AssistantHistoryRole.SYSTEM, UiText.Resource(R.string.assistant_history_reset)),
        )
        commandInput.text.clear()
        renderHistory()
    }

    fun recordResponse(state: AssistantExecutionState, force: Boolean = false) {
        val key = presentationKey(state)
        if (!force && key == AssistantConversationStore.lastPresentationKey) return
        AssistantConversationStore.lastPresentationKey = key
        val role = if (state.status == AssistantExecutionStatus.RUNNING_FORWARD ||
            state.status == AssistantExecutionStatus.RUNNING_REVERSE || state.notice != null
        ) AssistantHistoryRole.SYSTEM else AssistantHistoryRole.ASSISTANT
        mapper.response(state).forEach { AssistantConversationStore.add(AssistantHistoryEntry(role, it)) }
        renderHistory()
    }

    fun render(state: AssistantExecutionState) {
        val status = mapper.status(state.status)
        statusText.text = status.text.resolve(activity)
        statusText.setBackgroundResource(status.background)
        statusText.setTextColor(ContextCompat.getColor(activity, status.color))
        renderDraft(state)
        renderMissing(state)
        renderProgress(state)
        renderResult(state)
        staleText.visibility = if (state.resultStale) View.VISIBLE else View.GONE
        val running = state.status == AssistantExecutionStatus.RUNNING_FORWARD ||
            state.status == AssistantExecutionStatus.RUNNING_REVERSE
        val ready = state.session.confirmationState == DraftStatus.READY_FOR_CONFIRMATION && !running
        confirmButton.visibility = if (ready) View.VISIBLE else View.GONE
        continueButton.visibility = if (ready) View.VISIBLE else View.GONE
        val reverseRunning = state.status == AssistantExecutionStatus.RUNNING_REVERSE
        cancelButton.isEnabled = reverseRunning
        sendButton.isEnabled = !reverseRunning
        confirmButton.isEnabled = !reverseRunning
    }

    private fun send() {
        val message = commandInput.text.toString()
        AssistantConversationStore.inputText = message
        onSend?.invoke(message)
    }

    private fun renderHistory() {
        historyContainer.removeAllViews()
        val entries = AssistantConversationStore.snapshot()
        if (entries.isEmpty()) {
            historyContainer.addView(historyText(AssistantHistoryRole.ASSISTANT, activity.getString(R.string.assistant_status_ready)))
        } else {
            entries.forEach { entry -> historyContainer.addView(historyText(entry.role, entry.content.resolve(activity))) }
        }
        historyScroll.post { historyScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun historyText(role: AssistantHistoryRole, value: String) = TextView(activity).apply {
        val roleLabel = activity.getString(
            when (role) {
                AssistantHistoryRole.USER -> R.string.assistant_history_user
                AssistantHistoryRole.ASSISTANT -> R.string.assistant_history_assistant
                AssistantHistoryRole.SYSTEM -> R.string.assistant_history_system
            },
        )
        text = "$roleLabel\n$value"
        setTextColor(ContextCompat.getColor(activity, R.color.text_primary))
        textSize = 12f
        setPadding(activity.dp(10), activity.dp(8), activity.dp(10), activity.dp(8))
        setBackgroundResource(if (role == AssistantHistoryRole.USER) R.drawable.bg_nav_selected else R.drawable.bg_input)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also {
            it.bottomMargin = activity.dp(6)
        }
    }

    private fun renderDraft(state: AssistantExecutionState) {
        draftContainer.removeAllViews()
        val session = state.session
        modeText.setText(
            when (session.activeMode) {
                AssistantMode.FORWARD -> R.string.assistant_mode_forward
                AssistantMode.REVERSE -> R.string.assistant_mode_reverse
                null -> R.string.assistant_no_active_draft
            },
        )
        session.activeChipPosition?.let {
            activeChipText.text = activity.getString(R.string.assistant_active_chip, it)
            activeChipText.visibility = View.VISIBLE
        } ?: run { activeChipText.visibility = View.GONE }

        when (session.activeMode) {
            AssistantMode.FORWARD -> {
                val draft = session.forwardDraft
                draftContainer.addView(detailText(activity.getString(
                    R.string.assistant_forward_global_summary,
                    draft.chipCount?.toString() ?: missing(),
                    mapper.inlet(draft.highInlet).resolve(activity),
                ), true))
                val count = draft.chipCount ?: draft.chips.keys.maxOrNull() ?: 0
                (1..count).forEach { position ->
                    val chip = draft.chips[position]
                    draftContainer.addView(detailText(activity.getString(
                        R.string.assistant_chip_summary,
                        position,
                        chip?.chipType ?: missing(),
                        number(chip?.leftWidthMm),
                        number(chip?.rightWidthMm),
                        number(chip?.leftDistanceMm),
                        number(chip?.rightDistanceMm),
                    )))
                }
            }
            AssistantMode.REVERSE -> {
                val draft = session.reverseDraft
                draftContainer.addView(detailText(activity.getString(
                    R.string.assistant_reverse_global_summary,
                    mapper.inlet(draft.highInlet).resolve(activity),
                    draft.targetConcentrations.size,
                ), true))
                repeat(6) { index ->
                    draftContainer.addView(detailText(activity.getString(
                        R.string.assistant_target_summary,
                        index + 1,
                        number(draft.targetConcentrations.getOrNull(index)),
                    )))
                }
            }
            null -> Unit
        }
    }

    private fun renderMissing(state: AssistantExecutionState) {
        val messages = state.session.missingFields.map { mapper.missing(it).resolve(activity) }.toMutableList()
        if (state.validationIssues.isNotEmpty()) messages += activity.getString(R.string.assistant_validation_generic)
        state.session.pendingAmbiguity?.let { messages += mapper.clarification(it).resolve(activity) }
        state.error?.let { messages += mapper.error(it.code).resolve(activity) }
        missingText.visibility = if (messages.isEmpty()) View.GONE else View.VISIBLE
        if (messages.isNotEmpty()) {
            missingText.text = buildString {
                append(activity.getString(R.string.assistant_missing_title))
                messages.distinct().forEach { append("\n• ").append(it) }
            }
        }
    }

    private fun renderProgress(state: AssistantExecutionState) {
        val progress = state.reverseProgress
        val visible = state.status == AssistantExecutionStatus.RUNNING_REVERSE
        progressGroup.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) {
            val completed = progress?.completedEvaluations ?: 0
            val total = progress?.totalEvaluationBudget?.coerceAtLeast(1) ?: 1
            val percent = ((progress?.fraction ?: 0.0) * 100).roundToInt().coerceIn(0, 99)
            progressBar.progress = percent
            progressText.text = activity.getString(R.string.assistant_progress, completed, total, percent)
        }
    }

    private fun renderResult(state: AssistantExecutionState) {
        resultContainer.removeAllViews()
        when (val result = state.result) {
            is AssistantExecutionResult.Forward -> renderForwardResult(result)
            is AssistantExecutionResult.Reverse -> renderReverseResult(result)
            null -> Unit
        }
    }

    private fun renderForwardResult(result: AssistantExecutionResult.Forward) {
        val product = result.productResult
        resultContainer.addView(sectionTitle(activity.getString(R.string.assistant_forward_result_title)))
        resultContainer.addView(detailText(activity.getString(
            R.string.assistant_forward_result_summary,
            product.chipCount,
            forwardFormatter.modelLabel(product.modelIdentifier).resolve(activity),
        ), true))
        product.concentrations.forEachIndexed { index, value ->
            resultContainer.addView(detailText(activity.getString(R.string.assistant_forward_result_row, index + 1, value)))
        }
        result.warnings.forEach { resultContainer.addView(warningText(activity.getString(R.string.assistant_warning, it))) }
    }

    private fun renderReverseResult(result: AssistantExecutionResult.Reverse) {
        val product = result.productResult
        resultContainer.addView(sectionTitle(activity.getString(R.string.assistant_reverse_result_title)))
        resultContainer.addView(detailText(activity.getString(
            if (product.metrics.qualityPassed) R.string.assistant_reverse_quality_passed
            else R.string.assistant_reverse_quality_warning,
        ), true))
        val inlet = mapper.inlet(product.selectedHighInlet).resolve(activity)
        resultContainer.addView(detailText(activity.getString(
            R.string.assistant_reverse_result_summary,
            inlet,
            product.runtimeMs / 1_000.0,
            product.evaluationCount,
        ), true))
        resultContainer.addView(detailText(activity.getString(
            R.string.assistant_reverse_metrics,
            product.metrics.mae,
            product.metrics.rmse,
            product.metrics.maxAe,
            product.metrics.spearman,
        ), true))
        product.candidate.chips.forEachIndexed { index, chip ->
            resultContainer.addView(detailText(activity.getString(
                R.string.assistant_reverse_chip_result,
                chip.position,
                chip.chipType,
                chip.leftWidthMm,
                chip.rightWidthMm,
                chip.leftDistanceMm,
                chip.rightDistanceMm,
                result.request.targetConcentrations[index],
                product.recomputedConcentrations[index],
                product.perPositionErrors[index],
            )))
        }
        result.warnings.forEach { resultContainer.addView(warningText(activity.getString(R.string.assistant_warning, it))) }
    }

    private fun sectionTitle(value: String) = TextView(activity).apply {
        text = value
        setTextAppearance(R.style.TextAppearance_DrugChip_SectionTitle)
        setPadding(0, activity.dp(6), 0, activity.dp(6))
    }

    private fun detailText(value: String, bold: Boolean = false) = TextView(activity).apply {
        text = value
        setTextColor(ContextCompat.getColor(activity, R.color.text_primary))
        textSize = 12f
        setPadding(activity.dp(10), activity.dp(8), activity.dp(10), activity.dp(8))
        setBackgroundResource(R.drawable.bg_input)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also {
            it.bottomMargin = activity.dp(6)
        }
    }

    private fun warningText(value: String) = detailText(value).apply {
        setBackgroundResource(R.drawable.bg_status_warning)
        setTextColor(ContextCompat.getColor(activity, R.color.status_warning_text))
    }

    private fun number(value: Float?): String = value?.let { String.format(Locale.US, "%.3f", it) } ?: missing()
    private fun missing(): String = activity.getString(R.string.assistant_value_missing)

    private fun presentationKey(state: AssistantExecutionState): String = listOf(
        state.status,
        state.lastCommand?.javaClass?.name,
        state.notice,
        state.error,
        state.result?.hashCode(),
        state.resultStale,
    ).joinToString(":")

    private fun dismissKeyboard() {
        activity.currentFocus?.let { focused ->
            val manager = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            manager.hideSoftInputFromWindow(focused.windowToken, 0)
            focused.clearFocus()
        }
    }
}

private fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

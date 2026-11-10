package com.druggradientchip.android.ui.reverse

import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.druggradientchip.android.R
import com.druggradientchip.android.reverse.HighConcentrationInlet
import com.druggradientchip.android.reverse.ReverseRequest
import com.druggradientchip.android.reverse.ReverseTaskState
import kotlin.math.roundToInt

class ReverseWorkspaceView(
    private val activity: Activity,
    private val root: View,
    private val formatter: ReverseResultFormatter = ReverseResultFormatter(),
    private val errorMapper: ReverseErrorMapper = ReverseErrorMapper(),
) {
    private val inlet1Option: RadioButton = root.findViewById(R.id.inlet1HighOption)
    private val inlet2Option: RadioButton = root.findViewById(R.id.inlet2HighOption)
    private val targetContainer: LinearLayout = root.findViewById(R.id.reverseTargetContainer)
    private val configurationScroll: ScrollView = root.findViewById(R.id.reverseConfigurationScroll)
    private val resultScroll: ScrollView = root.findViewById(R.id.reverseResultScroll)
    private val runButton: Button = root.findViewById(R.id.runReverseButton)
    private val cancelButton: Button = root.findViewById(R.id.cancelReverseButton)
    private val statusText: TextView = root.findViewById(R.id.reverseStatusText)
    private val progressBar: ProgressBar = root.findViewById(R.id.reverseProgressBar)
    private val progressText: TextView = root.findViewById(R.id.reverseProgressText)
    private val resultMessage: TextView = root.findViewById(R.id.reverseResultMessage)
    private val resultDetails: View = root.findViewById(R.id.reverseResultDetails)
    private val qualityText: TextView = root.findViewById(R.id.reverseQualityText)
    private val summaryText: TextView = root.findViewById(R.id.reverseSummaryText)
    private val metricsText: TextView = root.findViewById(R.id.reverseMetricsText)
    private val chipResults: LinearLayout = root.findViewById(R.id.reverseChipResults)
    private val targetInputs = mutableListOf<EditText>()
    private var submittedTargets = FloatArray(0)

    var onRunRequested: (() -> Unit)? = null
    var onCancelRequested: (() -> Unit)? = null

    init {
        createTargetInputs()
        runButton.setOnClickListener {
            dismissKeyboard()
            onRunRequested?.invoke()
        }
        cancelButton.setOnClickListener { onCancelRequested?.invoke() }
    }

    fun renderForm(state: ReverseFormState) {
        inlet1Option.isChecked = state.selectedHighInlet == HighConcentrationInlet.INLET_1
        inlet2Option.isChecked = state.selectedHighInlet == HighConcentrationInlet.INLET_2
        state.targets.forEachIndexed { index, value -> targetInputs.getOrNull(index)?.setText(value) }
    }

    fun readFormState(): ReverseFormState = ReverseFormState(
        selectedHighInlet = if (inlet2Option.isChecked) HighConcentrationInlet.INLET_2 else HighConcentrationInlet.INLET_1,
        targets = targetInputs.map { it.text.toString() },
    )

    fun markSubmitted(request: ReverseRequest) {
        submittedTargets = request.targetConcentrations.copyOf()
    }

    fun restoreSubmittedTargets(targets: FloatArray) {
        submittedTargets = targets.copyOf()
    }

    fun submittedTargets(): FloatArray = submittedTargets.copyOf()

    fun renderFormError(error: Throwable) {
        setInputsEnabled(true)
        showStatus(activity.getString(R.string.reverse_error), R.drawable.bg_status_error, R.color.status_error_text)
        showMessage(errorMapper.message(error).resolve(activity), true)
    }

    fun renderState(state: ReverseTaskState) {
        when (state) {
            ReverseTaskState.Idle -> renderReady()
            ReverseTaskState.Validating -> renderRunning(0, 1, 0)
            is ReverseTaskState.Running -> {
                val total = state.progress.totalEvaluationBudget.coerceAtLeast(1)
                val completed = state.progress.completedEvaluations.coerceAtMost(total - 1)
                val percent = (state.progress.fraction * 100).roundToInt().coerceIn(0, 99)
                renderRunning(completed, total, percent)
            }
            is ReverseTaskState.Completed -> renderSuccess(state)
            ReverseTaskState.Canceled -> {
                setInputsEnabled(true)
                hideProgress()
                showStatus(activity.getString(R.string.reverse_canceled), R.drawable.bg_status_warning, R.color.status_warning_text)
                showMessage(activity.getString(R.string.reverse_canceled))
            }
            is ReverseTaskState.Error -> {
                setInputsEnabled(true)
                hideProgress()
                showStatus(activity.getString(R.string.reverse_error), R.drawable.bg_status_error, R.color.status_error_text)
                showMessage(errorMapper.message(state.code).resolve(activity), true)
            }
        }
    }

    private fun renderReady() {
        setInputsEnabled(true)
        hideProgress()
        showStatus(activity.getString(R.string.reverse_ready), R.drawable.bg_status_ready, R.color.status_ready_text)
        showMessage(activity.getString(R.string.reverse_result_empty))
    }

    private fun renderRunning(completed: Int, total: Int, percent: Int) {
        setInputsEnabled(false)
        progressBar.visibility = View.VISIBLE
        progressBar.progress = percent
        progressText.visibility = View.VISIBLE
        progressText.text = activity.getString(R.string.reverse_progress, completed, total, percent)
        showStatus(activity.getString(R.string.reverse_running), R.drawable.bg_status_running, R.color.status_running_text)
        showMessage(activity.getString(R.string.reverse_running))
    }

    private fun renderSuccess(state: ReverseTaskState.Completed) {
        setInputsEnabled(true)
        hideProgress()
        showStatus(activity.getString(R.string.reverse_success), R.drawable.bg_status_ready, R.color.status_ready_text)
        resultMessage.visibility = View.GONE
        resultDetails.visibility = View.VISIBLE
        val result = state.result
        val passed = result.metrics.qualityPassed
        qualityText.text = formatter.quality(result).resolve(activity)
        qualityText.setBackgroundResource(if (passed) R.drawable.bg_status_ready else R.drawable.bg_status_warning)
        qualityText.setTextColor(ContextCompat.getColor(activity, if (passed) R.color.status_ready_text else R.color.status_warning_text))
        summaryText.text = formatter.summary(result).resolve(activity)
        metricsText.text = formatter.metrics(result)
        chipResults.removeAllViews()
        val targets = submittedTargets.takeIf { it.size == result.chipCount }
            ?: readFormState().targets.map { it.toFloatOrNull() ?: Float.NaN }.toFloatArray()
        formatter.chips(result, targets).forEach { chipResults.addView(createChipResult(it)) }
        resultScroll.post { resultScroll.scrollTo(0, 0) }
    }

    private fun createTargetInputs() {
        repeat(3) { rowIndex ->
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                isBaselineAligned = false
            }
            repeat(2) { column ->
                val position = rowIndex * 2 + column + 1
                val field = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also {
                        if (column > 0) it.marginStart = activity.dp(10)
                    }
                }
                field.addView(TextView(activity).apply {
                    text = activity.getString(R.string.target_concentration, position)
                    setTextAppearance(R.style.TextAppearance_DrugChip_Label)
                })
                val input = EditText(activity).apply {
                    setBackgroundResource(R.drawable.bg_input)
                    setPadding(activity.dp(12), 0, activity.dp(12), 0)
                    setTextColor(ContextCompat.getColor(activity, R.color.text_primary))
                    textSize = 14f
                    isSingleLine = true
                    inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
                    imeOptions = if (position == 6) android.view.inputmethod.EditorInfo.IME_ACTION_DONE else android.view.inputmethod.EditorInfo.IME_ACTION_NEXT
                    contentDescription = activity.getString(R.string.target_concentration, position)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, activity.dp(48))
                    setSelectAllOnFocus(true)
                }
                registerFocusVisibility(input)
                targetInputs += input
                field.addView(input)
                row.addView(field)
            }
            if (rowIndex > 0) row.setPadding(0, activity.dp(6), 0, 0)
            targetContainer.addView(row)
        }
    }

    private fun createChipResult(item: ReverseChipDisplay): View = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(activity.dp(10), activity.dp(8), activity.dp(10), activity.dp(8))
        setBackgroundResource(R.drawable.bg_input)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also {
            it.bottomMargin = activity.dp(8)
        }
        val title = item.title.resolve(activity)
        val structure = item.structure.resolve(activity)
        val geometry = item.geometry.resolve(activity)
        val concentrations = item.concentrations.resolve(activity)
        contentDescription = activity.getString(R.string.reverse_chip_description, title, structure, geometry, concentrations)
        addView(resultText(title, true))
        addView(resultText(structure, false))
        addView(resultText(geometry, false))
        addView(resultText(concentrations, false))
    }

    private fun resultText(value: String, bold: Boolean) = TextView(activity).apply {
        text = value
        setTextColor(ContextCompat.getColor(activity, R.color.text_primary))
        textSize = if (bold) 14f else 12f
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun setInputsEnabled(enabled: Boolean) {
        inlet1Option.isEnabled = enabled
        inlet2Option.isEnabled = enabled
        targetInputs.forEach { it.isEnabled = enabled }
        runButton.isEnabled = enabled
        cancelButton.isEnabled = !enabled
    }

    private fun hideProgress() {
        progressBar.visibility = View.GONE
        progressText.visibility = View.GONE
    }

    private fun showMessage(message: String, error: Boolean = false) {
        resultDetails.visibility = View.GONE
        resultMessage.visibility = View.VISIBLE
        resultMessage.text = message
        resultMessage.setTextColor(ContextCompat.getColor(activity, if (error) R.color.status_error_text else R.color.text_secondary))
    }

    private fun showStatus(message: String, background: Int, color: Int) {
        statusText.text = message
        statusText.setBackgroundResource(background)
        statusText.setTextColor(ContextCompat.getColor(activity, color))
    }

    private fun registerFocusVisibility(input: EditText) {
        input.setOnFocusChangeListener { view, focused ->
            if (focused) configurationScroll.post {
                val bounds = Rect()
                view.getDrawingRect(bounds)
                configurationScroll.offsetDescendantRectToMyCoords(view, bounds)
                configurationScroll.smoothScrollTo(0, (bounds.top - activity.dp(16)).coerceAtLeast(0))
            }
        }
    }

    private fun dismissKeyboard() {
        activity.currentFocus?.let { focused ->
            val manager = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            manager.hideSoftInputFromWindow(focused.windowToken, 0)
            focused.clearFocus()
        }
    }
}

private fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

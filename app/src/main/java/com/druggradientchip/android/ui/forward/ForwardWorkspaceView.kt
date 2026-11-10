package com.druggradientchip.android.ui.forward

import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.druggradientchip.android.R

class ForwardWorkspaceView(
    private val activity: Activity,
    root: View,
    private val formatter: ForwardResultFormatter = ForwardResultFormatter(),
) {
    private val chipCountSpinner: Spinner = root.findViewById(R.id.chipCountSpinner)
    private val inlet1Input: EditText = root.findViewById(R.id.inlet1Input)
    private val inlet2Input: EditText = root.findViewById(R.id.inlet2Input)
    private val chipContainer: LinearLayout = root.findViewById(R.id.chipContainer)
    private val configurationScroll: ScrollView = root.findViewById(R.id.configurationScroll)
    private val resultScroll: ScrollView = root.findViewById(R.id.resultScroll)
    private val runButton: Button = root.findViewById(R.id.runButton)
    private val progressBar: ProgressBar = root.findViewById(R.id.progressBar)
    private val statusText: TextView = root.findViewById(R.id.statusText)
    private val resultChipCount: TextView = root.findViewById(R.id.resultChipCount)
    private val resultModelFamily: TextView = root.findViewById(R.id.resultModelFamily)
    private val resultMessage: TextView = root.findViewById(R.id.resultMessage)
    private val resultTable: LinearLayout = root.findViewById(R.id.resultTable)
    private val resultRows: LinearLayout = root.findViewById(R.id.resultRows)
    private val chipEditors = mutableListOf<ChipEditorViews>()
    private var suppressChipCountCallback = false

    var onChipCountChanged: ((Int) -> Unit)? = null
    var onRunRequested: (() -> Unit)? = null

    init {
        chipCountSpinner.adapter = spinnerAdapter(ForwardFormState.allowedCounts)
        chipCountSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!suppressChipCountCallback) {
                    onChipCountChanged?.invoke(ForwardFormState.allowedCounts[position])
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
        registerFocusVisibility(inlet1Input)
        registerFocusVisibility(inlet2Input)
        runButton.setOnClickListener {
            dismissKeyboard()
            onRunRequested?.invoke()
        }
    }

    fun renderForm(state: ForwardFormState) {
        inlet1Input.setText(state.inlet1)
        inlet2Input.setText(state.inlet2)
        val selected = ForwardFormState.allowedCounts.indexOf(state.chipCount)
        if (selected >= 0 && chipCountSpinner.selectedItemPosition != selected) {
            suppressChipCountCallback = true
            chipCountSpinner.setSelection(selected)
            suppressChipCountCallback = false
        }

        chipEditors.clear()
        chipContainer.removeAllViews()
        state.chips.forEach { chip ->
            val editor = createChipEditor(chip)
            chipEditors += editor
            chipContainer.addView(editor.root)
        }
    }

    fun readFormState(): ForwardFormState =
        ForwardFormState(
            chipCount = chipCountSpinner.selectedItem.toString().toInt(),
            inlet1 = inlet1Input.text.toString(),
            inlet2 = inlet2Input.text.toString(),
            chips = chipEditors.map { it.toState() },
        )

    fun renderState(state: ForwardUiState) {
        when (state) {
            ForwardUiState.Idle,
            ForwardUiState.Editing,
            ForwardUiState.Validating,
            -> {
                progressBar.visibility = View.GONE
                runButton.isEnabled = true
                showStatus(activity.getString(R.string.ready), R.drawable.bg_status_ready, R.color.status_ready_text)
                showEmptyResult(activity.getString(R.string.result_empty))
            }

            ForwardUiState.Running -> {
                progressBar.visibility = View.VISIBLE
                runButton.isEnabled = false
                showStatus(activity.getString(R.string.running), R.drawable.bg_status_running, R.color.status_running_text)
                showEmptyResult(activity.getString(R.string.running))
            }

            is ForwardUiState.Success -> {
                progressBar.visibility = View.GONE
                runButton.isEnabled = true
                showStatus(activity.getString(R.string.success), R.drawable.bg_status_ready, R.color.status_ready_text)
                resultChipCount.text = state.result.chipCount.toString()
                resultModelFamily.text = formatter.modelLabel(state.result.modelIdentifier).resolve(activity)
                resultMessage.visibility = View.GONE
                resultTable.visibility = View.VISIBLE
                resultRows.removeAllViews()
                state.result.concentrations.forEachIndexed { index, concentration ->
                    resultRows.addView(createResultRow(index + 1, concentration))
                }
                resultScroll.post { resultScroll.scrollTo(0, 0) }
            }

            is ForwardUiState.Error -> {
                progressBar.visibility = View.GONE
                runButton.isEnabled = true
                showStatus(activity.getString(R.string.error), R.drawable.bg_status_error, R.color.status_error_text)
                showEmptyResult(state.message.resolve(activity), isError = true)
            }
        }
    }

    private fun createChipEditor(chip: ForwardChipFormState): ChipEditorViews {
        val root = LayoutInflater.from(activity).inflate(R.layout.view_chip_editor, chipContainer, false)
        root.findViewById<TextView>(R.id.chipPosition).text = activity.getString(R.string.chip_position, chip.position)
        val chipType = root.findViewById<Spinner>(R.id.chipTypeSpinner).apply {
            adapter = spinnerAdapter(ForwardFormState.allowedChipTypes)
            setSelection(ForwardFormState.allowedChipTypes.indexOf(chip.chipType).coerceAtLeast(0))
            contentDescription = activity.getString(R.string.chip_type_description, chip.position, activity.getString(R.string.chip_type))
        }
        val leftWidth = root.bindInput(R.id.leftWidthInput, chip.leftWidthMm, chip.position, R.string.left_width)
        val rightWidth = root.bindInput(R.id.rightWidthInput, chip.rightWidthMm, chip.position, R.string.right_width)
        val leftDistance = root.bindInput(R.id.leftDistanceInput, chip.leftBendStraightDistanceMm, chip.position, R.string.left_distance)
        val rightDistance = root.bindInput(R.id.rightDistanceInput, chip.rightBendStraightDistanceMm, chip.position, R.string.right_distance)
        return ChipEditorViews(chip.position, root, chipType, leftWidth, rightWidth, leftDistance, rightDistance)
    }

    private fun View.bindInput(id: Int, value: String, position: Int, label: Int): EditText =
        findViewById<EditText>(id).also { input ->
            input.setText(value)
            input.hint = activity.getString(label)
            input.contentDescription = activity.getString(R.string.chip_input_description, position, activity.getString(label))
            input.setSelectAllOnFocus(true)
            registerFocusVisibility(input)
        }

    private fun registerFocusVisibility(input: EditText) {
        input.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                configurationScroll.post {
                    val bounds = Rect()
                    view.getDrawingRect(bounds)
                    configurationScroll.offsetDescendantRectToMyCoords(view, bounds)
                    configurationScroll.smoothScrollTo(0, (bounds.top - activity.dp(16)).coerceAtLeast(0))
                }
            }
        }
    }

    private fun createResultRow(position: Int, concentration: Float): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(activity.dp(10), 0, activity.dp(10), 0)
            minimumHeight = activity.dp(42)
            contentDescription = activity.getString(
                R.string.forward_result_row_description,
                activity.getString(R.string.result_position, position),
                activity.getString(R.string.result_concentration, concentration),
            )
            addView(resultCell(activity.getString(R.string.result_position, position), android.view.Gravity.START))
            addView(resultCell(activity.getString(R.string.result_concentration, concentration), android.view.Gravity.END))
        }

    private fun resultCell(value: String, cellGravity: Int): TextView =
        TextView(activity).apply {
            text = value
            gravity = cellGravity or android.view.Gravity.CENTER_VERTICAL
            setTextColor(ContextCompat.getColor(activity, R.color.text_primary))
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        }

    private fun showEmptyResult(message: String, isError: Boolean = false) {
        resultChipCount.setText(R.string.not_available_value)
        resultModelFamily.setText(R.string.not_available_value)
        resultRows.removeAllViews()
        resultTable.visibility = View.GONE
        resultMessage.visibility = View.VISIBLE
        resultMessage.text = message
        resultMessage.setTextColor(
            ContextCompat.getColor(activity, if (isError) R.color.status_error_text else R.color.text_secondary),
        )
    }

    private fun showStatus(message: String, background: Int, textColor: Int) {
        statusText.text = message
        statusText.setBackgroundResource(background)
        statusText.setTextColor(ContextCompat.getColor(activity, textColor))
    }

    private fun dismissKeyboard() {
        activity.currentFocus?.let { focused ->
            val manager = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            manager.hideSoftInputFromWindow(focused.windowToken, 0)
            focused.clearFocus()
        }
    }

    private fun <T> spinnerAdapter(values: List<T>): ArrayAdapter<T> =
        ArrayAdapter(activity, android.R.layout.simple_spinner_item, values).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

    private data class ChipEditorViews(
        val position: Int,
        val root: View,
        val chipType: Spinner,
        val leftWidth: EditText,
        val rightWidth: EditText,
        val leftDistance: EditText,
        val rightDistance: EditText,
    ) {
        fun toState(): ForwardChipFormState =
            ForwardChipFormState(
                position = position,
                chipType = chipType.selectedItem.toString(),
                leftWidthMm = leftWidth.text.toString(),
                rightWidthMm = rightWidth.text.toString(),
                leftBendStraightDistanceMm = leftDistance.text.toString(),
                rightBendStraightDistanceMm = rightDistance.text.toString(),
            )
    }
}

private fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

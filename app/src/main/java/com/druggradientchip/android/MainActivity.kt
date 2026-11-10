package com.druggradientchip.android

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.druggradientchip.android.assistant.AssistantCommand
import com.druggradientchip.android.assistant.AssistantExecutionController
import com.druggradientchip.android.assistant.AssistantExecutionRuntimeFactory
import com.druggradientchip.android.assistant.AssistantExecutionState
import com.druggradientchip.android.assistant.AssistantExecutionStatus
import com.druggradientchip.android.forward.ForwardRuntimeFactory
import com.druggradientchip.android.i18n.AppLanguage
import com.druggradientchip.android.i18n.AppLanguageController
import com.druggradientchip.android.reverse.HighConcentrationInlet
import com.druggradientchip.android.reverse.ReverseRuntimeFactory
import com.druggradientchip.android.reverse.ReverseTaskController
import com.druggradientchip.android.reverse.ReverseTaskState
import com.druggradientchip.android.ui.forward.ForwardChipFormState
import com.druggradientchip.android.ui.forward.ForwardFormState
import com.druggradientchip.android.ui.forward.ForwardUiController
import com.druggradientchip.android.ui.forward.ForwardUiState
import com.druggradientchip.android.ui.forward.ForwardWorkspaceView
import com.druggradientchip.android.ui.assistant.AssistantConversationStore
import com.druggradientchip.android.ui.assistant.AssistantWorkspaceView
import com.druggradientchip.android.ui.reverse.ReverseFormState
import com.druggradientchip.android.ui.reverse.ReverseRequestMapper
import com.druggradientchip.android.ui.reverse.ReverseWorkspaceView

class MainActivity : AppCompatActivity() {
    private lateinit var forwardWorkspace: ForwardWorkspaceView
    private lateinit var forwardController: ForwardUiController
    private lateinit var reverseWorkspace: ReverseWorkspaceView
    private lateinit var reverseController: ReverseTaskController
    private lateinit var assistantWorkspace: AssistantWorkspaceView
    private lateinit var assistantController: AssistantExecutionController
    private val reverseMapper = ReverseRequestMapper()
    private val stateHandler = Handler(Looper.getMainLooper())
    private var forwardForm = ForwardFormState()
    private var reverseForm = ReverseFormState()
    private var selectedModule = ProductModule.FORWARD
    private var reverseSubmittedTargets = FloatArray(0)
    private var lastReverseState: ReverseTaskState? = null
    private var lastAssistantState: AssistantExecutionState? = null
    private var suppressLanguageCallback = true

    private val reverseStatePoll = object : Runnable {
        override fun run() {
            if (::reverseController.isInitialized) {
                val next = reverseController.state
                if (next !== lastReverseState) {
                    lastReverseState = next
                    reverseWorkspace.renderState(next)
                }
            }
            stateHandler.postDelayed(this, REVERSE_STATE_POLL_MS)
        }
    }

    private val assistantStatePoll = object : Runnable {
        override fun run() {
            if (::assistantController.isInitialized) {
                val next = assistantController.state
                if (next !== lastAssistantState) {
                    assistantWorkspace.render(next)
                    assistantWorkspace.recordResponse(next)
                    lastAssistantState = next
                }
            }
            stateHandler.postDelayed(this, ASSISTANT_STATE_POLL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        applyWindowInsets()

        forwardForm = savedInstanceState?.toForwardFormState() ?: ForwardFormState()
        reverseForm = savedInstanceState?.toReverseFormState() ?: ReverseFormState()
        selectedModule = savedInstanceState?.getString(KEY_SELECTED_MODULE)
            ?.let { runCatching { ProductModule.valueOf(it) }.getOrNull() }
            ?: ProductModule.FORWARD
        reverseSubmittedTargets = savedInstanceState?.getFloatArray(KEY_REVERSE_SUBMITTED_TARGETS) ?: FloatArray(0)

        forwardWorkspace = ForwardWorkspaceView(this, findViewById(R.id.main))
        forwardController = ForwardUiController(ForwardRuntimeFactory.create(this))
        reverseWorkspace = ReverseWorkspaceView(this, findViewById(R.id.reverseWorkspace))
        reverseWorkspace.restoreSubmittedTargets(reverseSubmittedTargets)
        reverseController = ReverseRuntimeFactory.taskController(this)
        savedInstanceState?.getString(KEY_ASSISTANT_INPUT)?.let { AssistantConversationStore.inputText = it }
        assistantWorkspace = AssistantWorkspaceView(this, findViewById(R.id.assistantWorkspace))
        assistantController = AssistantExecutionRuntimeFactory.controller(this)

        bindForward()
        bindReverse()
        bindAssistant()
        bindNavigation()
        bindLanguageSelector()
        forwardWorkspace.renderForm(forwardForm)
        reverseWorkspace.renderForm(reverseForm)
        assistantWorkspace.render(assistantController.state)
        showModule(selectedModule)
    }

    override fun onResume() {
        super.onResume()
        stateHandler.removeCallbacks(reverseStatePoll)
        lastReverseState = null
        stateHandler.post(reverseStatePoll)
        lastAssistantState = null
        stateHandler.post(assistantStatePoll)
    }

    override fun onPause() {
        stateHandler.removeCallbacks(reverseStatePoll)
        stateHandler.removeCallbacks(assistantStatePoll)
        super.onPause()
    }

    private fun bindForward() {
        forwardWorkspace.onChipCountChanged = { nextCount ->
            forwardForm = forwardWorkspace.readFormState().withChipCount(nextCount)
            forwardWorkspace.renderForm(forwardForm)
            forwardWorkspace.renderState(ForwardUiState.Editing)
        }
        forwardWorkspace.onRunRequested = {
            forwardForm = forwardWorkspace.readFormState()
            forwardController.runPrediction(forwardForm) { state ->
                runOnUiThread { forwardWorkspace.renderState(state) }
            }
        }
    }

    private fun bindReverse() {
        reverseWorkspace.onRunRequested = run@{
            reverseForm = reverseWorkspace.readFormState()
            val request = try {
                reverseMapper.map(reverseForm)
            } catch (error: Throwable) {
                reverseWorkspace.renderFormError(error)
                return@run
            }
            reverseWorkspace.markSubmitted(request)
            reverseSubmittedTargets = request.targetConcentrations.copyOf()
            reverseController.start(request)
            lastReverseState = reverseController.state
            reverseWorkspace.renderState(reverseController.state)
        }
        reverseWorkspace.onCancelRequested = {
            reverseController.cancel()
            lastReverseState = reverseController.state
            reverseWorkspace.renderState(reverseController.state)
        }
    }

    private fun bindAssistant() {
        assistantWorkspace.onSend = { message ->
            if (message.isNotBlank()) assistantWorkspace.recordUser(message)
            val next = assistantController.submit(message)
            assistantWorkspace.render(next)
            assistantWorkspace.recordResponse(next, force = true)
            lastAssistantState = next
            val rejected = next.lastCommand is AssistantCommand.Clarify ||
                next.status == AssistantExecutionStatus.ERROR
            if (!rejected) assistantWorkspace.clearAcceptedInput()
        }
        assistantWorkspace.onConfirm = { submitAssistantControl("confirm") }
        assistantWorkspace.onCancel = { submitAssistantControl("cancel") }
        assistantWorkspace.onHelp = { submitAssistantControl("help") }
        assistantWorkspace.onReset = {
            val next = assistantController.submit("reset")
            assistantWorkspace.resetHistory()
            assistantWorkspace.render(next)
            lastAssistantState = next
        }
    }

    private fun submitAssistantControl(command: String) {
        val next = assistantController.submit(command)
        assistantWorkspace.render(next)
        assistantWorkspace.recordResponse(next, force = true)
        lastAssistantState = next
    }

    private fun bindNavigation() {
        findViewById<View>(R.id.forwardNavigation).setOnClickListener { showModule(ProductModule.FORWARD) }
        findViewById<View>(R.id.reverseNavigation).setOnClickListener { showModule(ProductModule.REVERSE) }
        findViewById<View>(R.id.assistantNavigation).setOnClickListener { showModule(ProductModule.ASSISTANT) }
    }

    private fun bindLanguageSelector() {
        val spinner = findViewById<Spinner>(R.id.languageSpinner)
        val languages = AppLanguage.values().toList()
        spinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            listOf(
                getString(R.string.language_follow_system),
                getString(R.string.language_english),
                getString(R.string.language_simplified_chinese),
            ),
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinner.setSelection(languages.indexOf(AppLanguageController.current()).coerceAtLeast(0), false)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressLanguageCallback) {
                    suppressLanguageCallback = false
                    return
                }
                val selected = languages[position]
                if (selected == AppLanguageController.current()) return
                forwardForm = forwardWorkspace.readFormState()
                reverseForm = reverseWorkspace.readFormState()
                reverseSubmittedTargets = reverseWorkspace.submittedTargets()
                assistantWorkspace.saveInput()
                AppLanguageController.apply(selected)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        spinner.post { suppressLanguageCallback = false }
    }

    private fun showModule(module: ProductModule) {
        if (::forwardWorkspace.isInitialized) {
            when (selectedModule) {
                ProductModule.FORWARD -> forwardForm = forwardWorkspace.readFormState()
                ProductModule.REVERSE -> {
                    reverseForm = reverseWorkspace.readFormState()
                    reverseSubmittedTargets = reverseWorkspace.submittedTargets()
                }
                ProductModule.ASSISTANT -> assistantWorkspace.saveInput()
            }
        }
        selectedModule = module
        findViewById<View>(R.id.forwardConfigurationPanel).visibility = if (module == ProductModule.FORWARD) View.VISIBLE else View.GONE
        findViewById<View>(R.id.forwardResultPanel).visibility = if (module == ProductModule.FORWARD) View.VISIBLE else View.GONE
        findViewById<View>(R.id.reverseWorkspace).visibility = if (module == ProductModule.REVERSE) View.VISIBLE else View.GONE
        findViewById<View>(R.id.assistantWorkspace).visibility = if (module == ProductModule.ASSISTANT) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.moduleTitle).setText(
            when (module) {
                ProductModule.FORWARD -> R.string.forward_title
                ProductModule.REVERSE -> R.string.reverse_title
                ProductModule.ASSISTANT -> R.string.assistant_title
            },
        )
        styleNavigation(findViewById(R.id.forwardNavigation), module == ProductModule.FORWARD)
        styleNavigation(findViewById(R.id.reverseNavigation), module == ProductModule.REVERSE)
        styleNavigation(findViewById(R.id.assistantNavigation), module == ProductModule.ASSISTANT)
        if (module == ProductModule.REVERSE) {
            reverseWorkspace.renderForm(reverseForm)
            lastReverseState = reverseController.state
            reverseWorkspace.renderState(reverseController.state)
        } else if (module == ProductModule.FORWARD) {
            forwardWorkspace.renderForm(forwardForm)
        } else {
            assistantWorkspace.render(assistantController.state)
        }
    }

    private fun styleNavigation(view: TextView, selected: Boolean) {
        view.setBackgroundResource(if (selected) R.drawable.bg_nav_selected else 0)
        view.setTextColor(ContextCompat.getColor(this, if (selected) R.color.brand_blue else R.color.text_secondary))
        view.setTypeface(view.typeface, if (selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
    }

    private fun applyWindowInsets() {
        val root = findViewById<View>(R.id.main)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            windowInsets
        }
        ViewCompat.requestApplyInsets(root)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::forwardWorkspace.isInitialized) {
            forwardForm = forwardWorkspace.readFormState()
            reverseForm = reverseWorkspace.readFormState()
            assistantWorkspace.saveInput()
        }
        forwardForm.writeTo(outState)
        reverseForm.writeTo(outState)
        outState.putString(KEY_SELECTED_MODULE, selectedModule.name)
        outState.putFloatArray(KEY_REVERSE_SUBMITTED_TARGETS, reverseSubmittedTargets)
        outState.putString(KEY_ASSISTANT_INPUT, AssistantConversationStore.inputText)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        stateHandler.removeCallbacks(reverseStatePoll)
        stateHandler.removeCallbacks(assistantStatePoll)
        if (::forwardController.isInitialized) forwardController.close()
        super.onDestroy()
    }

    companion object {
        const val KEY_CHIP_COUNT = "forward_chip_count"
        const val KEY_INLET1 = "forward_inlet1"
        const val KEY_INLET2 = "forward_inlet2"
        const val KEY_CHIP_TYPES = "forward_chip_types"
        const val KEY_LEFT_WIDTHS = "forward_left_widths"
        const val KEY_RIGHT_WIDTHS = "forward_right_widths"
        const val KEY_LEFT_DISTANCES = "forward_left_distances"
        const val KEY_RIGHT_DISTANCES = "forward_right_distances"
        const val KEY_SELECTED_MODULE = "selected_module"
        const val KEY_REVERSE_INLET = "reverse_high_inlet"
        const val KEY_REVERSE_TARGETS = "reverse_targets"
        const val KEY_REVERSE_SUBMITTED_TARGETS = "reverse_submitted_targets"
        const val KEY_ASSISTANT_INPUT = "assistant_input"
        private const val REVERSE_STATE_POLL_MS = 100L
        private const val ASSISTANT_STATE_POLL_MS = 100L
    }
}

enum class ProductModule { FORWARD, REVERSE, ASSISTANT }

private fun ForwardFormState.writeTo(bundle: Bundle) {
    bundle.putInt(MainActivity.KEY_CHIP_COUNT, chipCount)
    bundle.putString(MainActivity.KEY_INLET1, inlet1)
    bundle.putString(MainActivity.KEY_INLET2, inlet2)
    bundle.putStringArrayList(MainActivity.KEY_CHIP_TYPES, ArrayList(chips.map { it.chipType }))
    bundle.putStringArrayList(MainActivity.KEY_LEFT_WIDTHS, ArrayList(chips.map { it.leftWidthMm }))
    bundle.putStringArrayList(MainActivity.KEY_RIGHT_WIDTHS, ArrayList(chips.map { it.rightWidthMm }))
    bundle.putStringArrayList(MainActivity.KEY_LEFT_DISTANCES, ArrayList(chips.map { it.leftBendStraightDistanceMm }))
    bundle.putStringArrayList(MainActivity.KEY_RIGHT_DISTANCES, ArrayList(chips.map { it.rightBendStraightDistanceMm }))
}

private fun ReverseFormState.writeTo(bundle: Bundle) {
    bundle.putString(MainActivity.KEY_REVERSE_INLET, selectedHighInlet.name)
    bundle.putStringArrayList(MainActivity.KEY_REVERSE_TARGETS, ArrayList(targets))
}

private fun Bundle.toForwardFormState(): ForwardFormState {
    val chipCount = getInt(MainActivity.KEY_CHIP_COUNT, 6).coerceIn(4, 10)
    val chipTypes = getStringArrayList(MainActivity.KEY_CHIP_TYPES).orEmpty()
    val leftWidths = getStringArrayList(MainActivity.KEY_LEFT_WIDTHS).orEmpty()
    val rightWidths = getStringArrayList(MainActivity.KEY_RIGHT_WIDTHS).orEmpty()
    val leftDistances = getStringArrayList(MainActivity.KEY_LEFT_DISTANCES).orEmpty()
    val rightDistances = getStringArrayList(MainActivity.KEY_RIGHT_DISTANCES).orEmpty()
    return ForwardFormState(
        chipCount = chipCount,
        inlet1 = getString(MainActivity.KEY_INLET1, "0.0") ?: "0.0",
        inlet2 = getString(MainActivity.KEY_INLET2, "1.0") ?: "1.0",
        chips = (1..chipCount).map { position ->
            val fallback = ForwardFormState.defaultChip(position)
            val index = position - 1
            ForwardChipFormState(
                position = position,
                chipType = chipTypes.getOrNull(index) ?: fallback.chipType,
                leftWidthMm = leftWidths.getOrNull(index) ?: fallback.leftWidthMm,
                rightWidthMm = rightWidths.getOrNull(index) ?: fallback.rightWidthMm,
                leftBendStraightDistanceMm = leftDistances.getOrNull(index) ?: fallback.leftBendStraightDistanceMm,
                rightBendStraightDistanceMm = rightDistances.getOrNull(index) ?: fallback.rightBendStraightDistanceMm,
            )
        },
    )
}

private fun Bundle.toReverseFormState(): ReverseFormState = ReverseFormState(
    selectedHighInlet = getString(MainActivity.KEY_REVERSE_INLET)
        ?.let { runCatching { HighConcentrationInlet.valueOf(it) }.getOrNull() }
        ?: HighConcentrationInlet.INLET_1,
    targets = getStringArrayList(MainActivity.KEY_REVERSE_TARGETS)?.takeIf { it.size == 6 }
        ?: ReverseFormState.DEFAULT_TARGETS,
)

package com.druggradientchip.android

import android.content.res.Configuration
import android.os.LocaleList
import android.os.SystemClock
import android.view.View
import android.widget.EditText
import android.widget.RadioButton
import android.widget.Spinner
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.druggradientchip.android.i18n.AppLanguage
import com.druggradientchip.android.i18n.AppLanguageController
import com.druggradientchip.android.assistant.AssistantExecutionRuntimeFactory
import com.druggradientchip.android.assistant.AssistantExecutionStatus
import com.druggradientchip.android.ui.assistant.AssistantConversationStore
import java.io.FileInputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalizationInstrumentationTest {
    @After
    fun restoreFollowSystem() {
        runOnMainThread { AppLanguageController.apply(AppLanguage.FOLLOW_SYSTEM) }
    }

    @Test
    fun englishAndSimplifiedChineseResourcesAreCompleteAndFormatted() {
        val english = localizedContext("en")
        val chinese = localizedContext("zh-CN")

        assertEquals("Forward Prediction", english.getString(R.string.forward_title))
        assertEquals("正向预测", chinese.getString(R.string.forward_title))
        assertEquals("Reverse Prediction", english.getString(R.string.reverse_title))
        assertEquals("反向预测", chinese.getString(R.string.reverse_title))
        assertEquals("Chip 4", english.getString(R.string.chip_position, 4))
        assertEquals("芯片 4", chinese.getString(R.string.chip_position, 4))
        assertEquals("0.120000", english.getString(R.string.result_concentration, 0.12f))
        assertEquals("0.120000", chinese.getString(R.string.result_concentration, 0.12f))
        assertNotEquals(
            english.getString(R.string.forward_error_model_missing),
            chinese.getString(R.string.forward_error_model_missing),
        )
    }

    @Test
    fun runtimeSwitchPreservesForwardReverseAndNavigationState() {
        runOnMainThread { AppLanguageController.apply(AppLanguage.ENGLISH) }
        val original = launchActivity()
        try {
            SystemClock.sleep(300)
            runOnMainThread {
                original.findViewById<EditText>(R.id.inlet1Input).setText("0.25")
                original.findViewById<View>(R.id.reverseNavigation).performClick()
                reverseTargets(original).first().setText("0.123456")
                original.findViewById<RadioButton>(R.id.inlet2HighOption).isChecked = true
                original.findViewById<Spinner>(R.id.languageSpinner).setSelection(2)
            }
            val chinese = waitForResumedActivity(excluding = original)
            runOnMainThread {
                assertEquals("反向预测", chinese.findViewById<TextView>(R.id.moduleTitle).text.toString())
                assertEquals("0.123456", reverseTargets(chinese).first().text.toString())
                assertEquals("0.25", chinese.findViewById<EditText>(R.id.inlet1Input).text.toString())
                assertEquals(true, chinese.findViewById<RadioButton>(R.id.inlet2HighOption).isChecked)
                chinese.findViewById<Spinner>(R.id.languageSpinner).setSelection(1)
            }
            val english = waitForResumedActivity(excluding = chinese)
            runOnMainThread {
                assertEquals("Reverse Prediction", english.findViewById<TextView>(R.id.moduleTitle).text.toString())
                assertEquals("0.123456", reverseTargets(english).first().text.toString())
                assertEquals("0.25", english.findViewById<EditText>(R.id.inlet1Input).text.toString())
                assertEquals(true, english.findViewById<RadioButton>(R.id.inlet2HighOption).isChecked)
                english.finish()
            }
            SystemClock.sleep(300)
            val relaunched = launchActivity()
            runOnMainThread {
                assertEquals(AppLanguage.ENGLISH, AppLanguageController.current())
                assertEquals(1, relaunched.findViewById<Spinner>(R.id.languageSpinner).selectedItemPosition)
                assertEquals("Forward Prediction", relaunched.findViewById<TextView>(R.id.moduleTitle).text.toString())
                relaunched.finish()
            }
        } finally {
            runOnMainThread {
                resumedActivity()?.let { if (!it.isDestroyed) it.finish() }
            }
        }
    }

    @Test
    fun followSystemUsesTheCurrentResourceConfiguration() {
        runOnMainThread { AppLanguageController.apply(AppLanguage.FOLLOW_SYSTEM) }
        val activity = launchActivity()
        try {
            runOnMainThread {
                assertEquals(AppLanguage.FOLLOW_SYSTEM, AppLanguageController.current())
                assertEquals(0, activity.findViewById<Spinner>(R.id.languageSpinner).selectedItemPosition)
                assertEquals(
                    activity.getString(R.string.forward_title),
                    activity.findViewById<TextView>(R.id.moduleTitle).text.toString(),
                )
            }
        } finally {
            runOnMainThread { activity.finish() }
        }
    }

    @Test
    fun runtimeSwitchPreservesAssistantDraftInputAndSelectedModule() {
        runOnMainThread {
            AppLanguageController.apply(AppLanguage.ENGLISH)
            AssistantConversationStore.clear()
        }
        val original = launchActivity()
        try {
            runOnMainThread {
                AssistantExecutionRuntimeFactory.controller(original).submit("reset")
                original.findViewById<View>(R.id.assistantNavigation).performClick()
                original.findViewById<EditText>(R.id.assistantCommandInput).setText(
                    "Reverse target concentrations are 0.08 0.14 0.25 0.38 0.48 0.58",
                )
                original.findViewById<View>(R.id.assistantSendButton).performClick()
                original.findViewById<EditText>(R.id.assistantCommandInput).setText("Inlet1 high")
                original.findViewById<Spinner>(R.id.languageSpinner).setSelection(2)
            }
            val chinese = waitForResumedActivity(excluding = original)
            runOnMainThread {
                assertEquals("智能助手", chinese.findViewById<TextView>(R.id.moduleTitle).text.toString())
                assertEquals("Inlet1 high", chinese.findViewById<EditText>(R.id.assistantCommandInput).text.toString())
                val state = AssistantExecutionRuntimeFactory.controller(chinese).state
                assertEquals(AssistantExecutionStatus.COLLECTING, state.status)
                assertEquals(6, state.session.reverseDraft.targetConcentrations.size)
                chinese.findViewById<Spinner>(R.id.languageSpinner).setSelection(1)
            }
            val english = waitForResumedActivity(excluding = chinese)
            runOnMainThread {
                assertEquals("Assistant", english.findViewById<TextView>(R.id.moduleTitle).text.toString())
                assertEquals(6, AssistantExecutionRuntimeFactory.controller(english).state.session.reverseDraft.targetConcentrations.size)
                english.finish()
            }
        } finally {
            runOnMainThread { resumedActivity()?.let { if (!it.isDestroyed) it.finish() } }
        }
    }

    private fun localizedContext(languageTag: String) =
        InstrumentationRegistry.getInstrumentation().targetContext.let { context ->
            val configuration = Configuration(context.resources.configuration)
            configuration.setLocales(LocaleList.forLanguageTags(languageTag))
            context.createConfigurationContext(configuration)
        }

    private fun launchActivity(): MainActivity {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val component = "${instrumentation.targetContext.packageName}/${MainActivity::class.java.name}"
        instrumentation.uiAutomation.executeShellCommand("am start -W -n $component").use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
        }
        return waitForResumedActivity()
    }

    private fun reverseTargets(activity: MainActivity): List<EditText> {
        val result = mutableListOf<EditText>()
        fun collect(view: View) {
            if (view is EditText) result += view
            if (view is android.view.ViewGroup) repeat(view.childCount) { collect(view.getChildAt(it)) }
        }
        collect(activity.findViewById(R.id.reverseTargetContainer))
        return result
    }

    private fun waitForResumedActivity(excluding: MainActivity? = null): MainActivity {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < deadline) {
            var activity: MainActivity? = null
            runOnMainThread { activity = resumedActivity()?.takeIf { it !== excluding } }
            activity?.let { return it }
            SystemClock.sleep(50)
        }
        throw AssertionError("MainActivity did not reach RESUMED within 5 seconds")
    }

    private fun resumedActivity(): MainActivity? = ActivityLifecycleMonitorRegistry.getInstance()
        .getActivitiesInStage(Stage.RESUMED)
        .filterIsInstance<MainActivity>()
        .firstOrNull()

    private fun runOnMainThread(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }
}

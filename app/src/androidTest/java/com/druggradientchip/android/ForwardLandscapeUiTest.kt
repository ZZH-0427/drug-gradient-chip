package com.druggradientchip.android

import android.content.pm.ActivityInfo
import android.os.SystemClock
import android.view.View
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import java.io.FileInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ForwardLandscapeUiTest {
    @Test
    fun mainActivityUsesSensorLandscape() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getActivityInfo(
            android.content.ComponentName(context, MainActivity::class.java),
            0,
        )

        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, info.screenOrientation)
    }

    @Test
    fun n10KeepsChipTenAndPrimaryActionReachable() {
        withLaunchedActivity { activity ->
            runOnMainThread {
                assertTrue(activity.findViewById<View>(R.id.resultScroll).isShown)
                assertTrue(activity.findViewById<View>(R.id.runButton).isShown)
                activity.findViewById<Spinner>(R.id.chipCountSpinner).setSelection(6)
            }
            waitForChipCount(activity, 10)

            runOnMainThread {
                val container = activity.findViewById<LinearLayout>(R.id.chipContainer)
                assertEquals(10, container.childCount)
                assertEquals(
                    activity.getString(R.string.chip_position, 10),
                    container.getChildAt(9).findViewById<TextView>(R.id.chipPosition).text,
                )
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.runButton).visibility)
                assertTrue(activity.findViewById<View>(R.id.configurationScroll).canScrollVertically(1))
            }
        }
    }

    @Test
    fun recreationPreservesLandscapeFormValues() {
        withLaunchedActivity { activity ->
            runOnMainThread {
                activity.findViewById<Spinner>(R.id.chipCountSpinner).setSelection(6)
            }
            waitForChipCount(activity, 10)

            runOnMainThread {
                activity.findViewById<TextView>(R.id.inlet1Input).text = "0.25"
                activity.recreate()
            }
            val recreated = waitForResumedActivity(excluding = activity)

            runOnMainThread {
                assertEquals(6, recreated.findViewById<Spinner>(R.id.chipCountSpinner).selectedItemPosition)
                assertEquals("0.25", recreated.findViewById<TextView>(R.id.inlet1Input).text.toString())
                assertEquals(10, recreated.findViewById<LinearLayout>(R.id.chipContainer).childCount)
            }
        }
    }

    private fun withLaunchedActivity(block: (MainActivity) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val component = "${instrumentation.targetContext.packageName}/${MainActivity::class.java.name}"
        instrumentation.uiAutomation.executeShellCommand("am start -W -n $component").use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
        }
        val activity = waitForResumedActivity()
        try {
            block(activity)
        } finally {
            runOnMainThread {
                ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>()
                    .forEach { it.finish() }
            }
        }
    }

    private fun waitForChipCount(activity: MainActivity, expected: Int) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        var actual = 0
        while (SystemClock.uptimeMillis() < deadline) {
            runOnMainThread {
                actual = activity.findViewById<LinearLayout>(R.id.chipContainer).childCount
            }
            if (actual == expected) {
                return
            }
            SystemClock.sleep(50)
        }
        assertEquals(expected, actual)
    }

    private fun waitForResumedActivity(excluding: MainActivity? = null): MainActivity {
        val deadline = SystemClock.uptimeMillis() + 5_000
        var activity: MainActivity? = null
        while (SystemClock.uptimeMillis() < deadline) {
            runOnMainThread {
                activity = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>()
                    .firstOrNull { it !== excluding }
            }
            activity?.let { return it }
            SystemClock.sleep(50)
        }
        throw AssertionError("MainActivity did not reach RESUMED within 5 seconds")
    }

    private fun runOnMainThread(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }
}

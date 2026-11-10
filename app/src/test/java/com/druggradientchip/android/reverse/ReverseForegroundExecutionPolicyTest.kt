package com.druggradientchip.android.reverse

import org.junit.Assert.assertEquals
import org.junit.Test

class ReverseForegroundExecutionPolicyTest {
    @Test
    fun `active task acquires and releases wake lock around service`() {
        val events = mutableListOf<String>()
        val wakeLock = RecordingWakeLock(events)
        val policy = ReverseForegroundExecutionPolicy(
            wakeLock = wakeLock,
            startService = { events += "service-start" },
            stopService = { events += "service-stop" },
        )

        policy.start()
        policy.stop()

        assertEquals(listOf("wake-acquire", "service-start", "service-stop", "wake-release"), events)
    }

    @Test
    fun `service start failure releases wake lock`() {
        val events = mutableListOf<String>()
        val wakeLock = RecordingWakeLock(events)
        val policy = ReverseForegroundExecutionPolicy(
            wakeLock = wakeLock,
            startService = { error("start failed") },
            stopService = {},
        )

        runCatching(policy::start)

        assertEquals(listOf("wake-acquire", "wake-release"), events)
    }

    private class RecordingWakeLock(private val events: MutableList<String>) : ReverseTaskWakeLock {
        override fun acquire() { events += "wake-acquire" }
        override fun release() { events += "wake-release" }
    }
}

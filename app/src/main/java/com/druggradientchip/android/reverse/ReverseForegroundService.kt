package com.druggradientchip.android.reverse

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.druggradientchip.android.MainActivity
import com.druggradientchip.android.R
import com.druggradientchip.android.i18n.AppLanguageController

class ReverseForegroundService : Service() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguageController.localizedContext(newBase))
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(getString(R.string.reverse_notification_title))
            .setContentText(getString(R.string.reverse_notification_message))
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        isRunning = true
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }

    override fun onTimeout(startId: Int) {
        handleTimeout()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        handleTimeout()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun handleTimeout() {
        ReverseRuntimeFactory.cancelActive()
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.reverse_notification_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "reverse_prediction"
        private const val NOTIFICATION_ID = 2001

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}

internal class ReverseForegroundExecutionPolicy(
    private val wakeLock: ReverseTaskWakeLock,
    private val startService: () -> Unit,
    private val stopService: () -> Unit,
) : ActiveTaskExecutionPolicy {
    constructor(context: Context) : this(
        wakeLock = AndroidReverseTaskWakeLock(context),
        startService = {
            ContextCompat.startForegroundService(
                context.applicationContext,
                Intent(context.applicationContext, ReverseForegroundService::class.java),
            )
        },
        stopService = {
            context.applicationContext.stopService(
                Intent(context.applicationContext, ReverseForegroundService::class.java),
            )
        },
    )

    override fun start() {
        wakeLock.acquire()
        try {
            startService()
        } catch (error: Throwable) {
            wakeLock.release()
            throw error
        }
    }

    override fun stop() {
        try {
            stopService()
        } finally {
            wakeLock.release()
        }
    }
}

internal interface ReverseTaskWakeLock {
    fun acquire()
    fun release()
}

private class AndroidReverseTaskWakeLock(context: Context) : ReverseTaskWakeLock {
    private val wakeLock = context.getSystemService(PowerManager::class.java).newWakeLock(
        PowerManager.PARTIAL_WAKE_LOCK,
        "${context.packageName}:reverse-search",
    )

    override fun acquire() {
        if (!wakeLock.isHeld) wakeLock.acquire(MAX_HOLD_MILLIS)
    }

    override fun release() {
        if (wakeLock.isHeld) wakeLock.release()
    }

    companion object {
        private const val MAX_HOLD_MILLIS = 5 * 60 * 1000L
    }
}

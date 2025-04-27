package com.efojug.sleepy.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager

class MonitorService : Service() {
    private val CHANNEL_ID = "sleepy_monitor"

    override fun onCreate() {
        super.onCreate()
        val chan = NotificationChannel(
            CHANNEL_ID, "Sleepy", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(chan)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SleepyClient 运行中")
            .setContentText("喵喵喵")
            .build()

        val request = OneTimeWorkRequestBuilder<SyncWorker>().setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST).build()

        WorkManager.getInstance(context)
            .enqueue(request)

        ServiceCompat.startForeground(
            this, 1, notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) =
        START_STICKY

    override fun onBind(intent: Intent?) = null
}
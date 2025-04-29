package com.efojug.sleepy.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.efojug.sleepy.worker.StatusWorker
import java.util.concurrent.TimeUnit

class MonitorService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scheduleExpeditedStatusWork()
        return START_STICKY
    }

    private fun scheduleExpeditedStatusWork() {

        val expeditedRequest = OneTimeWorkRequestBuilder<StatusWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(applicationContext)
            .enqueueUniqueWork(
                "status_report_expedited",
                ExistingWorkPolicy.REPLACE,
                expeditedRequest
            )
    }

    private val handler = Handler(Looper.getMainLooper())
    private val interval = TimeUnit.MINUTES.toMillis(1)
    private val runnable = object : Runnable {
        override fun run() {
            scheduleStatusWork()
            handler.postDelayed(this, interval)
        }
    }

    override fun onCreate() {
        super.onCreate()
        // 1. 创建并启动前台通知
        val channelId = "sleepy"
        applicationContext.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(
                NotificationChannel(channelId, "后台保活", NotificationManager.IMPORTANCE_MIN)
            )
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Sleepy")
            .setContentText("喵喵喵")
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .build()
        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)

        // 2. 启动周期性调度
        handler.post(runnable)
    }

    private fun scheduleStatusWork() {
        val request = OneTimeWorkRequestBuilder<StatusWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(applicationContext)
            .enqueueUniqueWork("status_report", ExistingWorkPolicy.REPLACE, request)
    }

    override fun onBind(intent: Intent?) = null
}
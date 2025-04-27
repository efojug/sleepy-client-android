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
import androidx.work.workDataOf
import com.efojug.sleepy.MainActivity
import com.efojug.sleepy.datastore.PreferencesManager
import com.efojug.sleepy.worker.StatusWorker
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

class MonitorService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val interval = TimeUnit.MINUTES.toMillis(15)
    private val runnable = object : Runnable {
        override fun run() {
            scheduleStatusWork()
            handler.postDelayed(this, interval)
        }
    }

    override fun onCreate() {
        super.onCreate()
        // 1. 创建并启动前台通知
        val channelId = "sleepy_monitor"
        applicationContext.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(
                NotificationChannel(channelId, "Sleepy 常驻", NotificationManager.IMPORTANCE_MIN)
            )
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("SleepyClient 运行中")
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) =
        START_STICKY

    override fun onBind(intent: Intent?) = null
}
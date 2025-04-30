package com.efojug.sleepy.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.*
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.efojug.sleepy.worker.StatusWorker
import java.util.concurrent.TimeUnit

class MonitorService : Service() {
    companion object {
        private const val CHANNEL_ID = "sleepy"
        private const val WORK_NAME = "status_report"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var sleepMode = false
    private lateinit var screenReceiver: BroadcastReceiver

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerScreenReceiver()
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 启动前台服务
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sleepy")
            .setContentText("喵喵喵")
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .build()

        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)

        // 首次调度
        handler.post(runnable)
        return START_STICKY
    }

    private val runnable = object : Runnable {
        override fun run() {
            if (sleepMode) {
                // 睡眠模式下，仅检测屏幕亮起
                if (isScreenOn()) {
                    sleepMode = false
                    scheduleStatusWork() // 退出睡眠模式立即上报
                }
                // 下次5分钟后再检测
                handler.postDelayed(this, TimeUnit.MINUTES.toMillis(5))
            } else {
                scheduleStatusWork()
                handler.postDelayed(this, TimeUnit.MINUTES.toMillis(2))
            }
        }
    }

    private fun scheduleStatusWork() {
        val request = OneTimeWorkRequestBuilder<StatusWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        // 名称唯一，防止并发重复执行
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    private fun isScreenOn(): Boolean {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        return pm.isInteractive
    }

    private fun registerScreenReceiver() {
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> sleepMode = true
                    Intent.ACTION_SCREEN_ON -> {
                        // 立即处理亮屏事件
                        sleepMode = false
                        handler.removeCallbacks(runnable)
                        handler.post(runnable)
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, filter)
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Sleepy Service", NotificationManager.IMPORTANCE_MIN)
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        // 清理
        handler.removeCallbacks(runnable)
        unregisterReceiver(screenReceiver)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
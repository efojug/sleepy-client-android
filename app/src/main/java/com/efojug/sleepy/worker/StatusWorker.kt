package com.efojug.sleepy.worker

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.PowerManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.efojug.sleepy.network.DeviceStatus
import com.efojug.sleepy.network.RetrofitClient

class StatusWorker(
    context: Context, params: WorkerParameters
) : CoroutineWorker(context, params) {

    private fun hasUsagePermission(): Boolean {
        val appOps = applicationContext
            .getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            applicationContext.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override suspend fun doWork(): Result {
        val url = inputData.getString("REPORT_URL") ?: return Result.failure()
        val secret = inputData.getString("SECRET") ?: ""
        val device = inputData.getInt("DEVICE", 0)

        // UsageStats 权限校验
        if (!hasUsagePermission()) {
            return Result.failure()
        }

        // 前台应用包名获取
        val usm = applicationContext
            .getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, end - 5000, end
        )
        val pkg = stats.maxByOrNull { it.lastTimeUsed }?.packageName.orEmpty()

        // 屏幕状态检测
        val pm = applicationContext
            .getSystemService(Context.POWER_SERVICE) as PowerManager
        val status = if (pm.isInteractive) 1 else 0

        // 构造并上报
        val statusObj = DeviceStatus(secret, device, status, pkg)
        val api = RetrofitClient.create(url)
        return try {
            val resp = api.postStatus(url, statusObj)
            if (resp.isSuccessful) Result.success() else Result.retry()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
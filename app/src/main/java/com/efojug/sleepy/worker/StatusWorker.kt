package com.efojug.sleepy.worker

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.PowerManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.efojug.sleepy.datastore.PreferencesManager
import com.efojug.sleepy.network.DeviceStatus
import com.efojug.sleepy.network.RetrofitClient
import kotlinx.coroutines.flow.firstOrNull
import retrofit2.HttpException
import java.io.IOException

class StatusWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "StatusWorker"

        @Volatile
        private var lastPackageName: String? = null
        @Volatile
        private var lastAppName: String = ""
    }

    override suspend fun doWork(): Result {

        val url = PreferencesManager.urlFlow(applicationContext).firstOrNull().orEmpty()
        val secret = PreferencesManager.secretFlow(applicationContext).firstOrNull().orEmpty()
        val deviceId = PreferencesManager.deviceFlow(applicationContext).firstOrNull() ?: -1

        if (url.isBlank() || secret.isBlank() || deviceId == -1) {
            Log.e(TAG, "Input data missing: url=$url, secret=$secret, device=$deviceId")
            return Result.failure()
        }

        // UsageStats 权限校验
        if (!hasUsagePermission()) {
            return Result.failure()
        }

        val currentPackageName = getForegroundPackageName()

        // 如果本次查询为空，则回退至缓存
        val currentAppName = if (currentPackageName.isNullOrEmpty()) {
            if (lastPackageName.isNullOrEmpty()) "" else lastAppName // 若缓存也为空，再返回空字符串
        } else {
            lastPackageName = currentPackageName
            lastAppName = applicationContext.packageManager.getApplicationInfo(currentPackageName, 0).loadLabel(applicationContext.packageManager).toString()
            lastAppName
        }

        // 屏幕状态检测
        val pm = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val status = if (pm.isInteractive) 1 else 0

        return try {
            val api = RetrofitClient.create()
            val status = DeviceStatus(secret, deviceId.toInt(), status, currentAppName)
            val response = api.postStatus(url, status)

            if (response.isSuccessful) {
                Log.i(TAG, "Status reported successfully")
                Result.success()
            } else {
                Log.w(TAG, "Report failed with code: ${response.code()}")
                Result.retry()
            }
        } catch (e: HttpException) {
            Log.e(TAG, "HTTP error: ${e.code()} - ${e.message()}", e)
            Result.retry()
        } catch (e: IOException) {
            Log.e(TAG, "Network I/O error", e)
            Result.retry()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error reporting status", e)
            Result.failure()
        }
    }

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

    private fun getForegroundPackageName(): String? {
        // 前台应用包名获取
        val usm = applicationContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST, end - 60_000, end
        )
        return if (stats.isNullOrEmpty()) null else stats.maxByOrNull { it.lastTimeUsed }?.packageName
    }
}
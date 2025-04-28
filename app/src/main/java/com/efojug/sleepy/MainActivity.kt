package com.efojug.sleepy

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.efojug.sleepy.datastore.PreferencesManager
import com.efojug.sleepy.service.MonitorService
import com.efojug.sleepy.worker.StatusWorker
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import androidx.core.net.toUri

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        isGranted ->
            if (!isGranted) {
                Toast.makeText(this, "需要获取“通知”的权限", Toast.LENGTH_LONG).show()
            }
    }

    @SuppressLint("BatteryLife", "QueryPermissionsNeeded")
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when ((getSystemService(NOTIFICATION_SERVICE) as NotificationManager).areNotificationsEnabled()) {
            true -> null
            false -> {
                Toast.makeText(this, "需要获取“通知”的权限", Toast.LENGTH_LONG).show()
                requestPermissionLauncher.launch(
                    Manifest.permission.POST_NOTIFICATIONS
                )
            }
        }

        when ((getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)) {
            true -> null
            false -> {
                Toast.makeText(this, "需要忽略电池优化", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.setData("package:$packageName".toUri())
                if (intent.resolveActivity(packageManager) != null) startActivity(intent)
            }
        }

        // 检查 UsageStats 权限
        if (!hasUsageStatsPermission()) {
            Toast.makeText(this, "需要获取“有权查看使用情况的程序”的权限", Toast.LENGTH_LONG).show()
            startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            )
        }

        setContent {
            val scope = rememberCoroutineScope()
            var url by remember { mutableStateOf("") }
            var secret by remember { mutableStateOf("") }
            var deviceId by remember { mutableStateOf("") }

            // 从 DataStore 恢复
            LaunchedEffect(Unit) {
                launch {
                    PreferencesManager.urlFlow(this@MainActivity).collectLatest { url = it }
                }
                launch {
                    PreferencesManager.secretFlow(this@MainActivity).collectLatest { secret = it }
                }
                launch {
                    PreferencesManager.deviceFlow(this@MainActivity).collectLatest { deviceId = it.toString() }
                }
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("上报地址") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = secret,
                    onValueChange = { secret = it },
                    label = { Text("Secret") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = deviceId,
                    onValueChange = { deviceId = it },
                    label = { Text("Device ID") },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    // URL前缀检查
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        url = "https://$url"
                    }
//                    if (!url.endsWith("/")) url = "$url/"
                    // 保存配置
                    scope.launch {
                        PreferencesManager.saveConfig(
                            this@MainActivity, url, secret, deviceId.toIntOrNull() ?: 0
                        )
                    }
                    // 启动前台服务
                    startForegroundService(
                        Intent(this@MainActivity, MonitorService::class.java)
                    )
                    // 调度 Worker
                    val work = PeriodicWorkRequestBuilder<StatusWorker>(1, TimeUnit.MINUTES)
                        .setInputData(
                            workDataOf(
                                "REPORT_URL" to url,
                                "SECRET" to secret,
                                "DEVICE" to (deviceId.toIntOrNull() ?: 0)
                            )
                        )
                        .build()
                    WorkManager.getInstance(this@MainActivity).enqueueUniquePeriodicWork(
                        "StatusWork",
                        ExistingPeriodicWorkPolicy.REPLACE,
                        work
                    )
                    finish()  // 立即退出界面
                }) {
                    Text("开始监控")
                }
            }
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(), packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun scheduleStatusWork() {
        lifecycleScope.launch {
            val workRequest = OneTimeWorkRequestBuilder<StatusWorker>().build()

            WorkManager.getInstance(applicationContext)
                .enqueueUniqueWork(
                    "status_report_once",
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
        }

        // 完成后再次调度自身以模拟短周期
        Handler(Looper.getMainLooper()).postDelayed({
            scheduleStatusWork()
        }, TimeUnit.MINUTES.toMillis(1))
    }
}
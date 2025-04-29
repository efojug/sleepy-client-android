// MainActivity.kt (完整代码)
package com.efojug.sleepy

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
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
import androidx.core.net.toUri
import com.efojug.sleepy.datastore.PreferencesManager
import com.efojug.sleepy.service.MonitorService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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

        // 请求通知权限
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.areNotificationsEnabled()) {
            Toast.makeText(this, "需要获取“通知”的权限", Toast.LENGTH_LONG).show()
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // 请求忽略电池优化
        val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            Toast.makeText(this, "需要忽略电池优化", Toast.LENGTH_LONG).show()
            Intent(ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = "package:$packageName".toUri()
                startActivity(this)
            }
        }

        // 请求 UsageStats 权限
        if (!hasUsageStatsPermission()) {
            Toast.makeText(this, "需要获取“有权查看使用情况”的权限", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        setContent {
            val scope = rememberCoroutineScope()
            var url by remember { mutableStateOf("") }
            var secret by remember { mutableStateOf("") }
            var deviceId by remember { mutableStateOf("") }

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
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Number
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    // 确保 URL 前缀
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        url = "https://$url"
                    }
                    // 保存配置
                    scope.launch {
                        PreferencesManager.saveConfig(this@MainActivity, url, secret, deviceId.toIntOrNull() ?: 0)
                    }
                    // 启动 MonitorService 并隐藏 Activity
                    Intent(this@MainActivity, MonitorService::class.java).also {
                        startForegroundService(it)
                    }
                    finish()
                }) {
                    Text("开始监控")
                }
            }
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        return appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        ) == AppOpsManager.MODE_ALLOWED
    }
}

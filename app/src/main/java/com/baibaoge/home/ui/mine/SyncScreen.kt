package com.baibaoge.home.ui.mine

import android.Manifest
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.baibaoge.home.sync.LatestInfo
import com.baibaoge.home.sync.NetworkUtils
import com.baibaoge.home.sync.SyncConfigStore
import com.baibaoge.home.sync.SyncManager
import kotlinx.coroutines.launch

/**
 * 同步管理页（设计方案 3.5）：
 * NAS 地址/端口/WiFi SSID 白名单配置、连接测试、手动立即同步、
 * 从 NAS 下载最新备份恢复本地、同步日志入口。
 */
@Composable
fun SyncScreen(onBack: () -> Unit, onNavigateLogs: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var ip by remember { mutableStateOf(SyncConfigStore.ip(context)) }
    var port by remember { mutableStateOf(SyncConfigStore.port(context).toString()) }
    var ssidsRaw by remember { mutableStateOf(SyncConfigStore.ssidsRaw(context)) }

    var hasLocationPerm by remember { mutableStateOf(NetworkUtils.hasLocationPermission(context)) }
    var currentNet by remember { mutableStateOf("") }
    var latest by remember { mutableStateOf<LatestInfo?>(null) }
    var latestError by remember { mutableStateOf<String?>(null) }

    var testing by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasLocationPerm = granted
        if (granted) refreshNetwork(context) { currentNet = it }
    }

    fun refreshLatest() {
        scope.launch {
            val (info, error) = SyncManager.fetchLatestInfo(context)
            latest = info
            latestError = error
        }
    }

    LaunchedEffect(Unit) {
        refreshNetwork(context) { currentNet = it }
        refreshLatest()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        TextButton(onClick = onBack) { Text("返回") }
        Text("同步管理", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(vertical = 8.dp))

        // ── NAS 配置 ──
        Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("NAS 配置", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("NAS IP 地址") },
                    placeholder = { Text("如 192.168.1.10") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit).take(5) },
                    label = { Text("端口（默认 ${SyncConfigStore.DEFAULT_PORT}）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = ssidsRaw,
                    onValueChange = { ssidsRaw = it },
                    label = { Text("家庭 WiFi SSID 白名单") },
                    placeholder = { Text("多个 SSID 用英文逗号分隔") },
                    supportingText = { Text("自动同步仅在连接白名单中的 WiFi 时执行") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            SyncConfigStore.setIp(context, ip)
                            SyncConfigStore.setPort(
                                context, port.toIntOrNull() ?: SyncConfigStore.DEFAULT_PORT
                            )
                            SyncConfigStore.setSsidsRaw(context, ssidsRaw)
                            scope.launch { snackbarHostState.showSnackbar("配置已保存") }
                            refreshLatest()
                        },
                        enabled = !testing
                    ) { Text("保存配置") }
                    Spacer(Modifier.padding(start = 12.dp))
                    OutlinedButton(
                        onClick = {
                            SyncConfigStore.setIp(context, ip)
                            SyncConfigStore.setPort(
                                context, port.toIntOrNull() ?: SyncConfigStore.DEFAULT_PORT
                            )
                            testing = true
                            scope.launch {
                                val msg = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    runCatching {
                                        com.baibaoge.home.sync.NasClient.health(
                                            SyncConfigStore.ip(context), SyncConfigStore.port(context)
                                        )
                                        "连接成功"
                                    }.getOrElse { "连接失败：${it.message ?: "网络错误"}" }
                                }
                                testing = false
                                snackbarHostState.showSnackbar(msg)
                                refreshLatest()
                            }
                        },
                        enabled = !testing && ip.isNotBlank()
                    ) {
                        if (testing) CircularProgressIndicator(Modifier.height(18.dp))
                        else Text("测试连接")
                    }
                }
            }
        }

        // ── 网络状态 ──
        Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("网络状态", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(currentNet, style = MaterialTheme.typography.bodySmall)
                if (!hasLocationPerm) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "读取 WiFi 名称需要定位权限（仅本地使用，不上传）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = {
                        permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }) { Text("授权定位") }
                }
                Spacer(Modifier.height(4.dp))
                Text("NAS 最新备份：", style = MaterialTheme.typography.bodyMedium)
                Text(
                    latest?.let {
                        "${it.filename}（${it.size / 1024} KB，${it.createdAtIso}）"
                    } ?: latestError ?: "暂无备份",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ── 同步操作 ──
        Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("同步操作", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        SyncConfigStore.setIp(context, ip)
                        SyncConfigStore.setPort(context, port.toIntOrNull() ?: SyncConfigStore.DEFAULT_PORT)
                        SyncConfigStore.setSsidsRaw(context, ssidsRaw)
                        syncing = true
                        scope.launch {
                            val msg = SyncManager.syncNow(context, SyncManager.TYPE_MANUAL)
                            syncing = false
                            snackbarHostState.showSnackbar(msg)
                            refreshLatest()
                        }
                    },
                    enabled = !syncing
                ) {
                    if (syncing) CircularProgressIndicator(Modifier.height(18.dp))
                    else Text("立即同步")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showRestoreConfirm = true },
                    enabled = !restoring
                ) {
                    if (restoring) CircularProgressIndicator(Modifier.height(18.dp))
                    else Text("从 NAS 恢复最新备份")
                }
                Text(
                    "恢复会用 NAS 备份覆盖本地全部数据",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onNavigateLogs) { Text("查看同步日志") }
            }
        }
    }

    SnackbarHost(snackbarHostState)

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("确认恢复") },
            text = { Text("将下载 NAS 最新备份并覆盖本地全部数据（库存、地点、照片），此操作不可撤销。确定继续？") },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreConfirm = false
                    restoring = true
                    scope.launch {
                        val (needRestart, msg) = SyncManager.restoreLatest(context)
                        restoring = false
                        if (needRestart) {
                            restartApp(context)
                        } else {
                            snackbarHostState.showSnackbar(msg)
                        }
                    }
                }) { Text("恢复") }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) { Text("取消") }
            }
        )
    }
}

private fun refreshNetwork(context: Context, onUpdate: (String) -> Unit) {
    val app = context.applicationContext
    onUpdate(
        when {
            !NetworkUtils.isWifiConnected(app) && NetworkUtils.isCellular(app) ->
                "当前：移动网络（自动同步已禁止）"
            !NetworkUtils.isWifiConnected(app) -> "当前：未连接 WiFi"
            else -> {
                val ssid = NetworkUtils.currentSsid(app)
                if (ssid != null) "当前：WiFi「$ssid」"
                else "当前：已连接 WiFi（名称未授权读取）"
            }
        }
    )
}

/** 恢复完成后重启应用以重新加载数据库 */
private fun restartApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(intent)
    Runtime.getRuntime().exit(0)
}

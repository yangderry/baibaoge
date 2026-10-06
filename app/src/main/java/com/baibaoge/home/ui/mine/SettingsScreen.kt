package com.baibaoge.home.ui.mine

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.baibaoge.home.auth.AuthManager
import com.baibaoge.home.util.BatteryWhitelistHelper
import com.baibaoge.home.util.NotificationHelper
import com.baibaoge.home.util.ReminderSettings
import com.baibaoge.home.worker.ExpiryCheckWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设置页（设计方案 3.1.4 + 5.2）：
 * 临期天数配置（食品/药品/其他）、通知权限申请、电池优化白名单引导、立即检查。
 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val auth = remember { AuthManager.getInstance(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var foodDays by remember { mutableIntStateOf(ReminderSettings.foodDays(context)) }
    var medDays by remember { mutableIntStateOf(ReminderSettings.medicineDays(context)) }
    var otherDays by remember { mutableIntStateOf(ReminderSettings.otherDays(context)) }
    var hasNotifyPerm by remember { mutableStateOf(NotificationHelper.canNotify(context)) }
    var isWhitelisted by remember { mutableStateOf(BatteryWhitelistHelper.isWhitelisted(context)) }
    var checking by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        auth.externalActivityInFlight = false
        hasNotifyPerm = granted
    }

    LaunchedEffect(Unit) {
        hasNotifyPerm = NotificationHelper.canNotify(context)
        isWhitelisted = BatteryWhitelistHelper.isWhitelisted(context)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        TextButton(onClick = onBack) { Text("返回") }
        Text("设置", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(vertical = 8.dp))

        // ── 临期天数配置 ──
        Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("临期提醒天数", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                ReminderSlider("食品", foodDays, 1..30) {
                    foodDays = it
                    ReminderSettings.setFoodDays(context, it)
                }
                Spacer(Modifier.height(8.dp))
                ReminderSlider("药品", medDays, 1..60) {
                    medDays = it
                    ReminderSettings.setMedicineDays(context, it)
                }
                Spacer(Modifier.height(8.dp))
                ReminderSlider("其他", otherDays, 1..30) {
                    otherDays = it
                    ReminderSettings.setOtherDays(context, it)
                }
            }
        }

        // ── 通知权限 ──
        Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("通知权限", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(
                        if (hasNotifyPerm) "已开启" else "未开启",
                        color = if (hasNotifyPerm) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (!hasNotifyPerm) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            auth.externalActivityInFlight = true
                            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }) { Text("申请通知权限") }
                }
            }
        }

        // ── 电池优化白名单 ──
        Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("电池优化白名单", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(
                        if (isWhitelisted) "已加入" else "未加入",
                        color = if (isWhitelisted) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "若未加入白名单，系统可能在夜间清理后台，导致每日临期提醒无法按时触发。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    auth.externalActivityInFlight = true
                    BatteryWhitelistHelper.openVendorSettings(context)
                }) { Text(if (isWhitelisted) "重新设置" else "去设置") }
            }
        }

        // ── 立即检查 ──
        Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("立即检查", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "立刻扫描在库物品，推送临期通知。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        checking = true
                        scope.launch {
                            val count = withContext(Dispatchers.IO) {
                                ExpiryCheckWorker.runCheck(context)
                            }
                            checking = false
                            snackbarHostState.showSnackbar(
                                if (count > 0) "发现 $count 件临期物品，已推送通知"
                                else "暂无临期物品"
                            )
                        }
                    },
                    enabled = !checking
                ) {
                    if (checking) CircularProgressIndicator(Modifier.size(18.dp))
                    else Text("立即检查")
                }
            }
        }
    }

    SnackbarHost(snackbarHostState)
}

@Composable
private fun ReminderSlider(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(48.dp))
            Text("$value 天", style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt().coerceIn(range)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.count() - 2
        )
    }
}

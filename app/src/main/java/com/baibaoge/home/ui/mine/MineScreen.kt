package com.baibaoge.home.ui.mine

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.baibaoge.home.auth.AuthManager
import com.baibaoge.home.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 我的页：历史档案 / 同步管理 / 数据导出 / 设置入口 */
@Composable
fun MineScreen(
    onNavigateArchives: () -> Unit,
    onNavigateSync: () -> Unit,
    onNavigateSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val auth = remember { AuthManager.getInstance(context) }

    // 导出数据库文件到用户自选位置（SAF，无需存储权限）
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        auth.externalActivityInFlight = false
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val db = AppDatabase.getInstance(context)
                        // 合并 WAL，保证导出的主库文件包含最新数据
                        db.openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(FULL)")
                        val dbFile = context.getDatabasePath("baobaoge_db")
                        require(dbFile.exists()) { "数据库文件不存在" }
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            dbFile.inputStream().use { it.copyTo(out, 64 * 1024) }
                        } ?: error("无法创建目标文件")
                    }
                }
                snackbarHostState.showSnackbar(
                    result.fold(
                        onSuccess = { "数据库已导出（不含照片，完整备份请用同步管理）" },
                        onFailure = { "导出失败：${it.message ?: "未知错误"}" }
                    )
                )
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("我的", style = MaterialTheme.typography.headlineSmall)
        MineEntry("历史档案", "查看已归档物品，可恢复库存", onNavigateArchives)
        MineEntry("同步管理", "NAS 备份 / 恢复 / 同步日志", onNavigateSync)
        MineEntry("数据导出", "将数据库文件导出到手机存储，可自行留存或迁移") {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            auth.externalActivityInFlight = true
            exportLauncher.launch("baobaoge_db_$stamp.db")
        }
        MineEntry("设置", "临期提醒天数 / 通知权限 / 后台白名单", onNavigateSettings)
        SnackbarHost(snackbarHostState)
    }
}

@Composable
private fun MineEntry(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

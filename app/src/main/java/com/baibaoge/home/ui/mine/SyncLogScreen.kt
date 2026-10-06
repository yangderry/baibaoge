package com.baibaoge.home.ui.mine

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.baibaoge.home.data.AppDatabase
import com.baibaoge.home.data.entity.SyncLogEntity
import com.baibaoge.home.sync.SyncManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 同步日志页（设计方案 3.5）：
 * 最近 100 条同步记录：时间、类型（自动/手动）、方向（上传/下载）、结果、失败原因。
 */
@Composable
fun SyncLogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val logs by remember {
        AppDatabase.getInstance(context).syncLogDao().observeRecent()
    }.collectAsState(initial = emptyList())
    val timeFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onBack) { Text("返回") }
        Text("同步日志", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(vertical = 8.dp))

        if (logs.isEmpty()) {
            Text(
                "暂无同步记录",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp)
            )
        }
        LazyColumn {
            items(logs, key = { it.id }) { log ->
                SyncLogItem(log, timeFormat.format(Date(log.syncTime)))
            }
        }
    }
}

@Composable
private fun SyncLogItem(log: SyncLogEntity, timeText: String) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            androidx.compose.foundation.layout.Row {
                Text(
                    when (log.syncType) {
                        SyncManager.TYPE_AUTO -> "自动"
                        else -> "手动"
                    },
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    " · ${if (log.direction == "upload") "上传备份" else "下载恢复"} · $timeText",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                if (log.result == "success") "成功" else "失败",
                style = MaterialTheme.typography.titleSmall,
                color = if (log.result == "success") MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )
            if (!log.fileVersion.isNullOrBlank()) {
                Text(
                    "版本：${log.fileVersion}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!log.message.isNullOrBlank()) {
                Text(
                    log.message!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

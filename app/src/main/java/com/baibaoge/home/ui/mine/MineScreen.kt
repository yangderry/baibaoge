package com.baibaoge.home.ui.mine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 我的页：历史档案 / 同步管理 / 数据导出 / 设置入口 */
@Composable
fun MineScreen(onNavigateArchives: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("我的", style = MaterialTheme.typography.headlineSmall)
        MineEntry("历史档案", "查看已归档物品，可恢复库存", onNavigateArchives)
        MineEntry("同步管理", "NAS 备份与恢复（阶段 5 开放）") { }
        MineEntry("数据导出", "导出 CSV / 备份（阶段 6 开放）") { }
        MineEntry("设置", "修改 PIN / 指纹（阶段 6 开放）") { }
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

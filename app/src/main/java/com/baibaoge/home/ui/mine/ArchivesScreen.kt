package com.baibaoge.home.ui.mine

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.baibaoge.home.data.AppDatabase
import com.baibaoge.home.data.entity.ItemEntity
import com.baibaoge.home.ui.items.itemTypeName
import com.baibaoge.home.util.DateUtils
import kotlinx.coroutines.launch

/** 历史档案页：展示已归档物品，支持恢复库存 */
@Composable
fun ArchivesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val itemDao = remember { db.itemDao() }
    val scope = rememberCoroutineScope()

    val archived by itemDao.observeArchived().collectAsState(initial = emptyList())

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onBack) { Text("返回") }
        Text(
            "历史档案",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        if (archived.isEmpty()) {
            Text("暂无归档物品", modifier = Modifier.padding(32.dp))
        } else {
            LazyColumn {
                items(archived, key = { it.itemId }) { item ->
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Row(Modifier.padding(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${itemTypeName(item.itemType)} · 归档于 ${DateUtils.formatDate(item.updateTime)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = {
                                scope.launch {
                                    // 恢复库存：status 回 1，数量至少为 1
                                    itemDao.update(
                                        item.copy(
                                            status = ItemEntity.STATUS_IN_STOCK,
                                            quantity = maxOf(1, item.quantity),
                                            updateTime = System.currentTimeMillis()
                                        )
                                    )
                                }
                            }) { Text("恢复库存") }
                        }
                    }
                }
            }
        }
    }
}

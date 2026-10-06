package com.baibaoge.home.ui.locations

import androidx.compose.foundation.clickable
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
import com.baibaoge.home.data.entity.ItemEntity
import com.baibaoge.home.ui.items.itemTypeName
import com.baibaoge.home.util.DateUtils
import kotlinx.coroutines.flow.flow

/** 地点详情页：展示该地点下所有在库物品；可从扫码地点码进入 */
@Composable
fun LocationItemsScreen(
    locationId: String,
    onItemClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val locationDao = remember { db.locationDao() }
    val itemDao = remember { db.itemDao() }

    val location by remember(locationId) {
        flow { emit(locationDao.getById(locationId)) }
    }.collectAsState(initial = null)

    val items by itemDao.observeInStockByLocation(locationId).collectAsState(initial = emptyList())

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onBack) { Text("返回") }
        Text(
            location?.locationName ?: "未知地点",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Text(
            "在库物品 ${items.size} 件",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (items.isEmpty()) {
            Text("该地点暂无在库物品", modifier = Modifier.padding(32.dp))
        } else {
            LazyColumn {
                items(items, key = { it.itemId }) { item ->
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clickable { onItemClick(item.itemId) }
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(item.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${itemTypeName(item.itemType)} · 数量 ${item.quantity} · 到期 ${DateUtils.formatDate(item.expiryDate)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

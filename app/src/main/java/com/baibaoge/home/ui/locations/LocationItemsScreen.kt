package com.baibaoge.home.ui.locations

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.baibaoge.home.ui.items.ItemCard
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
                    ItemCard(
                        item = item,
                        locationName = location?.locationName,
                        onClick = { onItemClick(item.itemId) }
                    )
                }
            }
        }
    }
}

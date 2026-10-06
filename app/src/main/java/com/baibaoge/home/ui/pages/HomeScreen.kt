package com.baibaoge.home.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baibaoge.home.data.AppDatabase
import com.baibaoge.home.data.entity.ItemEntity
import com.baibaoge.home.ui.items.ItemCard
import com.baibaoge.home.ui.items.expiryBadge
import com.baibaoge.home.util.ReminderSettings

/**
 * 首页（阶段 6）：
 * 顶部临期物品列表（已过期优先，其余按剩余天数升序）；
 * 下方四大类物品数量统计卡片，点击可跳转到对应分类。
 */
@Composable
fun HomeScreen(
    onItemClick: (String) -> Unit,
    onGotoItems: (Int) -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val itemDao = remember { db.itemDao() }
    val locationDao = remember { db.locationDao() }

    val allItems by itemDao.observeInStock().collectAsState(initial = emptyList())
    val locations by locationDao.observeAll().collectAsState(initial = emptyList())
    val locationNames = remember(locations) { locations.associate { it.locationId to it.locationName } }

    // 临期：有到期日且落在各自类型的提醒阈值内，按到期先后排序（已过期排最前）
    val expiring = remember(allItems) {
        allItems.asSequence()
            .filter { e ->
                val d = e.expiryDate ?: 0L
                d > 0 && ReminderSettings.daysUntil(d) <=
                    ReminderSettings.thresholdFor(context, e.itemType)
            }
            .sortedBy { it.expiryDate }
            .toList()
    }

    val counts = remember(allItems) {
        mapOf(
            ItemEntity.TYPE_FOOD to allItems.count { it.itemType == ItemEntity.TYPE_FOOD },
            ItemEntity.TYPE_MEDICINE to allItems.count { it.itemType == ItemEntity.TYPE_MEDICINE },
            ItemEntity.TYPE_CLOTHING to allItems.count { it.itemType == ItemEntity.TYPE_CLOTHING },
            ItemEntity.TYPE_OTHER to allItems.count { it.itemType == ItemEntity.TYPE_OTHER }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
    ) {
        item {
            Text(
                "百宝格",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)
            )
        }

        // ===== 临期提醒 =====
        item {
            Text(
                if (expiring.isEmpty()) "临期提醒" else "临期提醒（${expiring.size}）",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
            )
        }
        if (expiring.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Text(
                        "近期没有临期物品，库存状态良好",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        } else {
            items(expiring, key = { it.itemId }) { item ->
                ItemCard(
                    item = item,
                    locationName = locationNames[item.locationId],
                    onClick = { onItemClick(item.itemId) }
                )
            }
        }

        // ===== 分类统计 =====
        item {
            Text(
                "库存统计",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
            )
        }
        item {
            val stats = listOf(
                Triple(ItemEntity.TYPE_FOOD, "食品", "🍚"),
                Triple(ItemEntity.TYPE_MEDICINE, "药品", "💊"),
                Triple(ItemEntity.TYPE_CLOTHING, "衣物", "👕"),
                Triple(ItemEntity.TYPE_OTHER, "其他", "📦")
            )
            Column(Modifier.padding(horizontal = 16.dp)) {
                stats.chunked(2).forEach { rowStats ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowStats.forEach { (type, label, emoji) ->
                            StatCard(
                                label = label,
                                emoji = emoji,
                                count = counts[type] ?: 0,
                                modifier = Modifier.weight(1f),
                                onClick = { onGotoItems(type) }
                            )
                        }
                        if (rowStats.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    emoji: String,
    count: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .padding(end = 0.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(emoji, style = MaterialTheme.typography.headlineSmall)
            }
            Column(Modifier.padding(start = 12.dp)) {
                Text(
                    "$count",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "$label · 件",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

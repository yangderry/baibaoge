package com.baibaoge.home.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baibaoge.home.data.AppDatabase
import com.baibaoge.home.data.entity.ItemEntity
import com.baibaoge.home.ui.items.ExpiryYellow
import com.baibaoge.home.ui.items.expiryBadge
import com.baibaoge.home.ui.theme.ClothingTint
import com.baibaoge.home.ui.theme.ClothingTintDark
import com.baibaoge.home.ui.theme.FoodTint
import com.baibaoge.home.ui.theme.FoodTintDark
import com.baibaoge.home.ui.theme.MedicineTint
import com.baibaoge.home.ui.theme.MedicineTintDark
import com.baibaoge.home.ui.theme.OtherTint
import com.baibaoge.home.ui.theme.OtherTintDark
import com.baibaoge.home.util.DateUtils
import com.baibaoge.home.util.ReminderSettings
import androidx.compose.foundation.isSystemInDarkTheme
import java.util.Calendar

/**
 * 首页：问候语与库存概览 → 临期物品（按到期先后）→ 四大类统计卡片。
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
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
    ) {
        // ===== 顶部问候 =====
        item { HomeHeader(total = allItems.size, expiringCount = expiring.size) }

        // ===== 临期提醒 =====
        item {
            Text(
                if (expiring.isEmpty()) "临期提醒" else "临期提醒 · ${expiring.size}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp)
            )
        }
        if (expiring.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) { Text("✓", color = MaterialTheme.colorScheme.onPrimary) }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("库存状态良好", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "近期没有临期物品",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            items(expiring, key = { it.itemId }) { item ->
                ExpiringCard(
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
                modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 8.dp)
            )
        }
        item {
            val dark = isSystemInDarkTheme()
            val stats = listOf(
                Stat(ItemEntity.TYPE_FOOD, "食品", "🍚", if (dark) FoodTintDark else FoodTint),
                Stat(ItemEntity.TYPE_MEDICINE, "药品", "💊", if (dark) MedicineTintDark else MedicineTint),
                Stat(ItemEntity.TYPE_CLOTHING, "衣物", "👕", if (dark) ClothingTintDark else ClothingTint),
                Stat(ItemEntity.TYPE_OTHER, "其他", "📦", if (dark) OtherTintDark else OtherTint)
            )
            Column(Modifier.padding(horizontal = 20.dp)) {
                stats.chunked(2).forEach { rowStats ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowStats.forEach { s ->
                            StatCard(
                                stat = s,
                                count = counts[s.type] ?: 0,
                                modifier = Modifier.weight(1f),
                                onClick = { onGotoItems(s.type) }
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

private data class Stat(
    val type: Int,
    val label: String,
    val emoji: String,
    val tint: Color
)

@Composable
private fun HomeHeader(total: Int, expiringCount: Int) {
    val cal = Calendar.getInstance()
    val month = cal.get(Calendar.MONTH) + 1
    val day = cal.get(Calendar.DAY_OF_MONTH)
    val week = when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> "周一"
        Calendar.TUESDAY -> "周二"
        Calendar.WEDNESDAY -> "周三"
        Calendar.THURSDAY -> "周四"
        Calendar.FRIDAY -> "周五"
        Calendar.SATURDAY -> "周六"
        else -> "周日"
    }
    val greeting = when (cal.get(Calendar.HOUR_OF_DAY)) {
        in 5..10 -> "早上好"
        in 11..13 -> "中午好"
        in 14..17 -> "下午好"
        else -> "晚上好"
    }
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
        Text(
            "$greeting 👋",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            "今天是 ${month}月${day}日 $week",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primary)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "在库物品",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                )
                Text(
                    "$total 件",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "近期临期",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                )
                Text(
                    "$expiringCount 件",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** 首页临期卡片：左侧等级色条，右侧物品信息 */
@Composable
private fun ExpiringCard(item: ItemEntity, locationName: String?, onClick: () -> Unit) {
    val badge = expiryBadge(item)
    val barColor = badge?.second ?: ExpiryYellow
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(56.dp)
                    .background(barColor)
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    badge?.let { (label, color) ->
                        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
                    }
                }
                Text(
                    "到期 ${DateUtils.formatDate(item.expiryDate)} · ${locationName ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    stat: Stat,
    count: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(stat.tint),
                contentAlignment = Alignment.Center
            ) {
                Text(stat.emoji, style = MaterialTheme.typography.titleLarge)
            }
            Column(Modifier.padding(start = 12.dp)) {
                Text(
                    "$count",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stat.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

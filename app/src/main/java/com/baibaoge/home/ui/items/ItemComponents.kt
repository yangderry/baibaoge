package com.baibaoge.home.ui.items

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.baibaoge.home.data.entity.ItemEntity
import com.baibaoge.home.ui.components.AsyncImageBox
import com.baibaoge.home.util.DateUtils
import com.baibaoge.home.util.ReminderSettings

/** 临期分级色（设计方案 3.1.4）：≤3 天红，3-7 天橙，>7 天黄 */
val ExpiryRed = Color(0xFFE53935)
val ExpiryOrange = Color(0xFFFB8C00)
val ExpiryYellow = Color(0xFFF9A825)

/** 物品类型中文名 */
fun itemTypeName(type: Int): String = when (type) {
    ItemEntity.TYPE_FOOD -> "食品"
    ItemEntity.TYPE_MEDICINE -> "药品"
    ItemEntity.TYPE_CLOTHING -> "衣物"
    else -> "其他"
}

/** 解析物品的自定义标签（逗号分隔，兼容中英文逗号） */
fun parseTags(raw: String?): List<String> =
    (raw ?: "")
        .split(',', '，')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()

/** 临期文案与颜色（已过期/今天/剩 N 天）；不在提醒阈值内返回 null */
@Composable
fun expiryBadge(item: ItemEntity): Pair<String, Color>? {
    val expiry = item.expiryDate ?: return null
    if (expiry <= 0) return null
    val context = LocalContext.current
    val daysLeft = ReminderSettings.daysUntil(expiry)
    if (daysLeft > ReminderSettings.thresholdFor(context, item.itemType)) return null
    return when {
        daysLeft < 0 -> "已过期 ${-daysLeft} 天" to ExpiryRed
        daysLeft == 0 -> "今天到期" to ExpiryRed
        daysLeft <= 3 -> "剩 $daysLeft 天" to ExpiryRed
        daysLeft <= 7 -> "剩 $daysLeft 天" to ExpiryOrange
        else -> "剩 $daysLeft 天" to ExpiryYellow
    }
}

/** 小型标签 Chip（仅展示） */
@Composable
fun TagChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/** 物品卡片（列表共用，含照片缩略图、临期标识、标签） */
@Composable
fun ItemCard(item: ItemEntity, locationName: String?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImageBox(
                path = item.photoPath,
                contentDescription = item.name,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Column(Modifier.weight(1f)) {
                Row {
                    Text(item.name, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    Text("x${item.quantity}", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.width(8.dp))
                Row {
                    Text(
                        itemTypeName(item.itemType),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "地点：${locationName ?: item.locationId}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                expiryBadge(item)?.let { (label, color) ->
                    Text(
                        "到期：${DateUtils.formatDate(item.expiryDate)} · $label",
                        style = MaterialTheme.typography.bodySmall,
                        color = color
                    )
                }
                val tags = parseTags(item.tags)
                if (tags.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        tags.take(4).forEach { TagChip(it) }
                        if (tags.size > 4) TagChip("+${tags.size - 4}")
                    }
                }
            }
        }
    }
}

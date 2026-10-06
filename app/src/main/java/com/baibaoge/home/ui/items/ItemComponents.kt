package com.baibaoge.home.ui.items

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.baibaoge.home.data.entity.ItemEntity
import com.baibaoge.home.util.DateUtils
import com.baibaoge.home.util.ImageUtils

/** 物品类型中文名 */
fun itemTypeName(type: Int): String = when (type) {
    ItemEntity.TYPE_FOOD -> "食品"
    ItemEntity.TYPE_MEDICINE -> "药品"
    ItemEntity.TYPE_CLOTHING -> "衣物"
    else -> "其他"
}

/** 物品卡片（列表共用，含照片缩略图） */
@Composable
fun ItemCard(item: ItemEntity, locationName: String?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ImageUtils.loadBitmap(item.photoPath)?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = item.name,
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(56.dp),
                    contentScale = ContentScale.Crop
                )
            }
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
                if (item.expiryDate != null) {
                    Text(
                        "到期：${DateUtils.formatDate(item.expiryDate)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

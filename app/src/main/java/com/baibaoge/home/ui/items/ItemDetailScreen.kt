package com.baibaoge.home.ui.items

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.baibaoge.home.data.AppDatabase
import com.baibaoge.home.data.entity.ItemEntity
import com.baibaoge.home.ui.components.AsyncImageBox
import com.baibaoge.home.util.DateUtils
import com.baibaoge.home.util.QrCodeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 物品详情页：+1/-1 快捷改数量，减至 0 自动归档；全字段编辑、删除、二维码入口 */
@Composable
fun ItemDetailScreen(
    itemId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val itemDao = remember { db.itemDao() }
    val locationDao = remember { db.locationDao() }
    val scope = rememberCoroutineScope()

    val item by remember(itemId) { itemDao.observeById(itemId) }
        .collectAsState(initial = null)
    val locations by locationDao.observeAll().collectAsState(initial = emptyList())
    val locationNames = remember(locations) { locations.associate { it.locationId to it.locationName } }

    var showArchiveDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var qrPath by remember { mutableStateOf<String?>(null) }

    val current = item
    if (current == null) {
        Column(Modifier.fillMaxSize().padding(32.dp)) { Text("加载中…") }
        return
    }

    fun changeQuantity(delta: Int) {
        val newQ = current.quantity + delta
        if (newQ < 0) return
        if (newQ == 0) {
            showArchiveDialog = true
        } else {
            scope.launch {
                itemDao.updateQuantity(itemId, newQ, System.currentTimeMillis())
            }
        }
    }

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(current.name, style = MaterialTheme.typography.headlineSmall)

            current.photoPath?.takeIf { it.isNotBlank() }?.let { path ->
                AsyncImageBox(
                    path = path,
                    contentDescription = "物品照片",
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { changeQuantity(-1) }) { Text("-1") }
                Text("  数量：${current.quantity}  ", style = MaterialTheme.typography.titleLarge)
                OutlinedButton(onClick = { changeQuantity(1) }) { Text("+1") }
            }

            DetailRow("类型", itemTypeName(current.itemType))
            DetailRow("存放地点", locationNames[current.locationId] ?: current.locationId)
            DetailRow("条码", current.barcode ?: "未设置")
            DetailRow("购买日期", DateUtils.formatDate(current.purchaseDate))
            DetailRow("生产日期", DateUtils.formatDate(current.produceDate))
            DetailRow("到期日期", DateUtils.formatDate(current.expiryDate))
            DetailRow("购买渠道", current.purchaseChannel ?: "未设置")
            DetailRow("标签", current.tags ?: "无")
            DetailRow("创建时间", DateUtils.formatDate(current.createTime))
            DetailRow("更新时间", DateUtils.formatDate(current.updateTime))

            Spacer(Modifier.padding(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onEdit(itemId) }, modifier = Modifier.weight(1f)) { Text("编辑") }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            qrPath = current.itemQrPath?.takeIf { it.isNotBlank() }
                                ?: withContext(Dispatchers.IO) {
                                    QrCodeUtil.generateToFile(
                                        context,
                                        QrCodeUtil.itemContent(itemId),
                                        "$itemId.png"
                                    )?.also { path ->
                                        itemDao.update(current.copy(itemQrPath = path))
                                    }
                                }
                            if (qrPath != null) showQrDialog = true
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("二维码") }
            }
            OutlinedButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("返回") }
        }
    }

    if (showArchiveDialog) {
        AlertDialog(
            onDismissRequest = { showArchiveDialog = false },
            title = { Text("确认归档") },
            text = { Text("数量将减至 0，物品自动归档并移出主列表。") },
            confirmButton = {
                TextButton(onClick = {
                    showArchiveDialog = false
                    scope.launch {
                        itemDao.updateQuantity(itemId, 0, System.currentTimeMillis())
                        itemDao.updateStatus(itemId, ItemEntity.STATUS_ARCHIVED, System.currentTimeMillis())
                        onBack()
                    }
                }) { Text("确认归档") }
            },
            dismissButton = {
                TextButton(onClick = { showArchiveDialog = false }) { Text("取消") }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("物理删除后不可恢复，确定继续？") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    scope.launch { itemDao.delete(current); onBack() }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            }
        )
    }

    if (showQrDialog) {
        AlertDialog(
            onDismissRequest = { showQrDialog = false },
            title = { Text("物品二维码") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImageBox(
                        path = qrPath,
                        contentDescription = "物品二维码",
                        modifier = Modifier.size(240.dp)
                    )
                    Text(
                        current.itemId,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showQrDialog = false }) { Text("关闭") }
            }
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.weight(2f))
    }
}

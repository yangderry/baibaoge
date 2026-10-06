package com.baibaoge.home.ui.locations

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.baibaoge.home.data.AppDatabase
import com.baibaoge.home.data.entity.LocationEntity
import com.baibaoge.home.util.ImageUtils
import com.baibaoge.home.util.QrCodeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 地点页：列表 + 编辑/删除（有关联在库物品禁止删除，设计方案 3.2）。
 * 新增地点对话框由主界面 MainScreen 统一持有渲染，避免跨组件状态信号丢失。
 */
@Composable
fun LocationsScreen() {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val locationDao = remember { db.locationDao() }
    val itemDao = remember { db.itemDao() }
    val scope = rememberCoroutineScope()

    val locations by locationDao.observeWithItemCount().collectAsState(initial = emptyList())

    var editing by remember { mutableStateOf<LocationEntity?>(null) }
    var deleting by remember { mutableStateOf<LocationEntity?>(null) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var qrForLocation by remember { mutableStateOf<LocationEntity?>(null) }
    var qrDialogPath by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        Text(
            "存放地点",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp)
        )
        if (locations.isEmpty()) {
            Text("暂无地点，点击底部 ⊕ 新增", modifier = Modifier.padding(32.dp))
        } else {
            LazyColumn {
                items(locations, key = { it.location.locationId }) { loc ->
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(loc.location.locationName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "在库物品 ${loc.itemCount} 件",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = {
                                scope.launch {
                                    val path = loc.location.qrPath?.takeIf { it.isNotBlank() }
                                        ?: withContext(Dispatchers.IO) {
                                            QrCodeUtil.generateToFile(
                                                context,
                                                QrCodeUtil.locationContent(loc.location.locationId),
                                                "${loc.location.locationId}.png"
                                            )?.also { p ->
                                                locationDao.update(loc.location.copy(qrPath = p))
                                            }
                                        }
                                    if (path != null) {
                                        qrDialogPath = path
                                        qrForLocation = loc.location
                                    }
                                }
                            }) { Text("二维码") }
                            TextButton(onClick = { editing = loc.location }) { Text("编辑") }
                            TextButton(onClick = {
                                scope.launch {
                                    val count = itemDao.countInStockByLocation(loc.location.locationId)
                                    if (count > 0) {
                                        deleteError = "「${loc.location.locationName}」下还有 $count 件在库物品，无法删除"
                                    } else {
                                        deleting = loc.location
                                    }
                                }
                            }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }

    editing?.let { old ->
        LocationEditDialog(
            initial = old.locationName,
            title = "编辑地点",
            onDismiss = { editing = null },
            onConfirm = { name ->
                scope.launch {
                    locationDao.insert(old.copy(locationName = name))
                    editing = null
                }
            }
        )
    }

    deleting?.let { loc ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除地点") },
            text = { Text("确定删除「${loc.locationName}」？") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { locationDao.delete(loc) }
                    deleting = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } }
        )
    }

    deleteError?.let { msg ->
        AlertDialog(
            onDismissRequest = { deleteError = null },
            title = { Text("无法删除") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { deleteError = null }) { Text("知道了") } }
        )
    }

    qrForLocation?.let { loc ->
        AlertDialog(
            onDismissRequest = { qrForLocation = null },
            title = { Text(loc.locationName) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    qrDialogPath?.let { ImageUtils.loadBitmap(it) }?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "地点二维码",
                            modifier = Modifier.size(240.dp)
                        )
                    } ?: Text("二维码生成失败")
                    Text(
                        loc.locationId,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { qrForLocation = null }) { Text("关闭") }
            }
        )
    }
}

/** 地点名称编辑对话框（新增/编辑共用） */
@Composable
fun LocationEditDialog(
    initial: String = "",
    title: String = "新增地点",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("地点名称") }, singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name.trim()) }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

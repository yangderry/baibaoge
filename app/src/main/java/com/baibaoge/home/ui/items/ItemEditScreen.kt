package com.baibaoge.home.ui.items

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.baibaoge.home.data.AppDatabase
import com.baibaoge.home.data.entity.ItemEntity
import com.baibaoge.home.ui.components.PhotoPicker
import com.baibaoge.home.ui.navigation.ItemPrefill
import com.baibaoge.home.util.DateUtils
import com.baibaoge.home.util.IdGenerator
import com.baibaoge.home.util.QrCodeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/** 手动录入/编辑物品（itemId 为空表示新增）；prefill 为扫码/OCR/语音录入带入的预填数据 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemEditScreen(itemId: String?, prefill: ItemPrefill? = null, onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val itemDao = remember { db.itemDao() }
    val locationDao = remember { db.locationDao() }
    val scope = rememberCoroutineScope()
    val isEdit = !itemId.isNullOrBlank()

    var name by rememberSaveable { mutableStateOf(if (isEdit) "" else prefill?.name.orEmpty()) }
    var itemType by rememberSaveable { mutableIntStateOf(ItemEntity.TYPE_FOOD) }
    var quantity by rememberSaveable { mutableStateOf("1") }
    var locationId by rememberSaveable { mutableStateOf("") }
    var barcode by rememberSaveable { mutableStateOf(if (isEdit) "" else prefill?.barcode.orEmpty()) }
    var purchaseChannel by rememberSaveable { mutableStateOf("") }
    var tags by rememberSaveable { mutableStateOf("") }
    var purchaseDate by rememberSaveable { mutableLongStateOf(0L) }
    var produceDate by rememberSaveable { mutableLongStateOf(if (isEdit) 0L else prefill?.produceDate ?: 0L) }
    var expiryDate by rememberSaveable { mutableLongStateOf(if (isEdit) 0L else prefill?.expiryDate ?: 0L) }
    var photoPath by rememberSaveable { mutableStateOf(if (isEdit) "" else prefill?.photoPath.orEmpty()) }
    var original by remember { mutableStateOf<ItemEntity?>(null) }

    val locations by locationDao.observeAll().collectAsState(initial = emptyList())
    val locationNames = remember(locations) { locations.associate { it.locationId to it.locationName } }

    LaunchedEffect(itemId) {
        if (isEdit) {
            itemDao.getById(itemId!!)?.let { e ->
                original = e
                name = e.name
                itemType = e.itemType
                quantity = e.quantity.toString()
                locationId = e.locationId
                barcode = e.barcode ?: ""
                purchaseChannel = e.purchaseChannel ?: ""
                tags = e.tags ?: ""
                purchaseDate = e.purchaseDate ?: 0L
                produceDate = e.produceDate ?: 0L
                expiryDate = e.expiryDate ?: 0L
                photoPath = e.photoPath ?: ""
            }
        }
    }

    fun pickDate(current: Long, onPick: (Long) -> Unit) {
        val cal = Calendar.getInstance()
        if (current > 0) cal.timeInMillis = current
        DatePickerDialog(
            context,
            { _, y, m, d ->
                cal.set(y, m, d, 0, 0, 0)
                onPick(cal.timeInMillis)
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    val canSave = name.isNotBlank() && locationId.isNotBlank() &&
            (quantity.toIntOrNull() ?: 0) >= 0

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                if (isEdit) "编辑物品" else "新增物品",
                style = MaterialTheme.typography.headlineSmall
            )

            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("名称 *") }, modifier = Modifier.fillMaxWidth(), singleLine = true
            )

            var typeExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                OutlinedTextField(
                    value = itemTypeName(itemType), onValueChange = {},
                    readOnly = true, label = { Text("类型") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                    listOf(
                        ItemEntity.TYPE_FOOD, ItemEntity.TYPE_MEDICINE,
                        ItemEntity.TYPE_CLOTHING, ItemEntity.TYPE_OTHER
                    ).forEach { t ->
                        DropdownMenuItem(
                            text = { Text(itemTypeName(t)) },
                            onClick = { itemType = t; typeExpanded = false }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = quantity,
                onValueChange = { quantity = it.filter(Char::isDigit) },
                label = { Text("数量 *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )

            var locExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = locExpanded, onExpandedChange = { locExpanded = it }) {
                OutlinedTextField(
                    value = locationNames[locationId] ?: "", onValueChange = {},
                    readOnly = true, label = { Text("存放地点 *") },
                    placeholder = { Text(if (locations.isEmpty()) "请先在地点页新增地点" else "请选择") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(locExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(expanded = locExpanded, onDismissRequest = { locExpanded = false }) {
                    locations.forEach { loc ->
                        DropdownMenuItem(
                            text = { Text(loc.locationName) },
                            onClick = { locationId = loc.locationId; locExpanded = false }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = barcode, onValueChange = { barcode = it },
                label = { Text("商品条码（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            OutlinedTextField(
                value = purchaseChannel, onValueChange = { purchaseChannel = it },
                label = { Text("购买渠道（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            OutlinedTextField(
                value = tags, onValueChange = { tags = it },
                label = { Text("标签，逗号分隔（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true
            )

            DateRow("购买日期", purchaseDate, { pickDate(purchaseDate) { purchaseDate = it } }, { purchaseDate = 0L })
            DateRow("生产日期", produceDate, { pickDate(produceDate) { produceDate = it } }, { produceDate = 0L })
            DateRow("到期日期", expiryDate, { pickDate(expiryDate) { expiryDate = it } }, { expiryDate = 0L })

            Text("物品照片", style = MaterialTheme.typography.titleSmall)
            PhotoPicker(
                photoPath = photoPath.ifBlank { null },
                onPhotoChange = { photoPath = it.orEmpty() }
            )

            Spacer(Modifier.padding(4.dp))
            Button(
                onClick = {
                    scope.launch {
                        val now = System.currentTimeMillis()
                        val q = quantity.toIntOrNull() ?: 1
                        val base = original
                        val newItemId = base?.itemId ?: IdGenerator.itemId(itemType)
                        val qrPath = base?.itemQrPath ?: withContext(Dispatchers.IO) {
                            QrCodeUtil.generateToFile(
                                context,
                                QrCodeUtil.itemContent(newItemId),
                                "$newItemId.png"
                            )
                        }
                        val entity = ItemEntity(
                            itemId = newItemId,
                            itemType = itemType,
                            name = name.trim(),
                            barcode = barcode.trim().ifBlank { null },
                            purchaseDate = purchaseDate.takeIf { it > 0 },
                            produceDate = produceDate.takeIf { it > 0 },
                            expiryDate = expiryDate.takeIf { it > 0 },
                            quantity = q,
                            purchaseChannel = purchaseChannel.trim().ifBlank { null },
                            photoPath = photoPath.trim().ifBlank { null },
                            locationId = locationId,
                            itemQrPath = qrPath,
                            status = if (q > 0) ItemEntity.STATUS_IN_STOCK else (base?.status ?: ItemEntity.STATUS_IN_STOCK),
                            tags = tags.trim().ifBlank { null },
                            createTime = base?.createTime ?: now,
                            updateTime = now
                        )
                        if (isEdit) itemDao.update(entity) else itemDao.insert(entity)
                        onBack()
                    }
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth()
            ) { Text("保存") }
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("取消") }
        }
    }
}

@Composable
private fun DateRow(label: String, value: Long, onPick: () -> Unit, onClear: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Text(DateUtils.formatDate(value.takeIf { it > 0 }))
        TextButton(onClick = onPick) { Text("选择") }
        if (value > 0) TextButton(onClick = onClear) { Text("清除") }
    }
}

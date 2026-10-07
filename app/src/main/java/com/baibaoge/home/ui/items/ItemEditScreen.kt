package com.baibaoge.home.ui.items

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.material3.rememberDatePickerState
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
import java.util.TimeZone

/** 手动录入/编辑物品（itemId 为空表示新增）；prefill 为扫码/OCR/语音录入带入的预填数据 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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

    // 正在编辑哪个日期字段（null = 未弹出选择器）；M3 DatePicker 带年份下拉与输入模式，跨年选择更快
    var pickingDate by remember { mutableStateOf<DateFieldKind?>(null) }

    // ---- M3 日期选择器弹窗 ----
    pickingDate?.let { field ->
        val initialMillis = when (field) {
            DateFieldKind.PURCHASE -> purchaseDate
            DateFieldKind.PRODUCE -> produceDate
            DateFieldKind.EXPIRE -> expiryDate
        }.let { millis ->
            // DatePicker 内部用 UTC 零时；将本地日期先转 UTC 午夜传入，选中后再转回本地
            if (millis > 0) millis.toUtcMidnight() else null
        }
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            initialDisplayMode = androidx.compose.material3.DisplayMode.Picker // 先显示日历视图，可切输入
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = null },
            confirmButton = {
                TextButton(onClick = {
                    val picked = pickerState.selectedDateMillis
                    if (picked != null) {
                        val local = picked.fromUtcMidnight()
                        when (field) {
                            DateFieldKind.PURCHASE -> purchaseDate = local
                            DateFieldKind.PRODUCE -> produceDate = local
                            DateFieldKind.EXPIRE -> expiryDate = local
                        }
                    }
                    pickingDate = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { pickingDate = null }) { Text("取消") }
            }
        ) { DatePicker(state = pickerState, showModeToggle = true) }
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
                label = { Text("自定义标签（可选）") },
                supportingText = { Text("多个标签用逗号分隔，如：常备,儿童") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )

            // ---- 日期三行：M3 选择器 + 保质期推算 + 购买日期快捷键 ----
            DateRow(
                label = "购买日期", value = purchaseDate,
                onPick = { pickingDate = DateFieldKind.PURCHASE },
                onClear = { purchaseDate = 0L },
                trailing = {
                    TextButton(onClick = { purchaseDate = todayLocalMidnight() }) { Text("今天") }
                }
            )
            DateRow(
                label = "生产日期", value = produceDate,
                onPick = { pickingDate = DateFieldKind.PRODUCE },
                onClear = { produceDate = 0L }
            )
            DateRow(
                label = "到期日期", value = expiryDate,
                onPick = { pickingDate = DateFieldKind.EXPIRE },
                onClear = { expiryDate = 0L }
            )

            // 保质期快捷推算：基于已选的生产日期计算到期日，覆盖大多数包装标注习惯
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "按保质期推算到期日：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SHELF_LIFE_PRESETS.forEach { months ->
                        AssistChip(
                            onClick = {
                                if (produceDate <= 0) {
                                    Toast.makeText(context, "请先选择生产日期", Toast.LENGTH_SHORT).show()
                                } else {
                                    expiryDate = addMonths(produceDate, months)
                                }
                            },
                            label = { Text("${months}个月") }
                        )
                    }
                }
            }

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

// ===== 日期选择辅助 =====

/** 物品的三个日期字段，用于区分弹窗确认后写入哪个状态 */
private enum class DateFieldKind { PURCHASE, PRODUCE, EXPIRE }

/** 常用保质期（月） */
private val SHELF_LIFE_PRESETS = listOf(3, 6, 12, 18, 24, 36)

/** 本地毫秒 → 本地当日 0 点的毫秒（用于「今天」快捷键） */
private fun todayLocalMidnight(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** 将本地毫秒时间转换为 UTC 零时对应毫秒（供 DatePicker 使用） */
private fun Long.toUtcMidnight(): Long {
    val local = Calendar.getInstance().apply { timeInMillis = this@toUtcMidnight }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

/** 将 DatePicker 返回的 UTC 毫秒转回本地时区毫秒 */
private fun Long.fromUtcMidnight(): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = this@fromUtcMidnight }
    return Calendar.getInstance().apply {
        clear()
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

/** 在本地毫秒上加 N 个月（用于保质期推算） */
private fun addMonths(baseMillis: Long, months: Int): Long = Calendar.getInstance().apply {
    timeInMillis = baseMillis
    add(Calendar.MONTH, months)
}.timeInMillis

@Composable
private fun DateRow(
    label: String,
    value: Long,
    onPick: () -> Unit,
    onClear: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Text(DateUtils.formatDate(value.takeIf { it > 0 }))
        trailing?.invoke()
        TextButton(onClick = onPick) { Text("选择") }
        if (value > 0) TextButton(onClick = onClear) { Text("清除") }
    }
}

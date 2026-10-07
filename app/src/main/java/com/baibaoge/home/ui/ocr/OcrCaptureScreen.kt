package com.baibaoge.home.ui.ocr

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.baibaoge.home.ui.navigation.ItemPrefill
import com.baibaoge.home.util.DateUtils
import com.baibaoge.home.util.ImagePreprocessor
import com.baibaoge.home.util.ImageUtils
import com.baibaoge.home.util.OcrParser
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 拍照 OCR 录入页（设计方案 3.2）：
 * CameraX 拍照 → 压缩保存 → ML Kit 中文离线识别 →
 * 用户从识别文字中点选填充名称/生产日期/到期日期，确认后才跳表单，不自动写库。
 */
@Composable
fun OcrCaptureScreen(
    onConfirm: (ItemPrefill) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasPermission = it }
    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }

    val imageCapture = remember { ImageCapture.Builder().build() }
    var processing by remember { mutableStateOf(false) }
    var photoPath by remember { mutableStateOf<String?>(null) }
    var ocrResult by remember { mutableStateOf<OcrParser.Result?>(null) }
    var captureError by remember { mutableStateOf<String?>(null) }

    fun capture() {
        val tmp = File(context.cacheDir, "ocr_${System.currentTimeMillis()}.jpg")
        val opts = ImageCapture.OutputFileOptions.Builder(tmp).build()
        imageCapture.takePicture(
            opts, ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    processing = true
                    scope.launch(Dispatchers.IO) {
                        // OCR 用原图高分辨率解码+预处理；展示/存储仍走 1280px 压缩图
                        val ocrBase = ImagePreprocessor.decodeForOcr(tmp)
                        val path = ImageUtils.compressToPrivate(
                            context, tmp, "photo_${System.currentTimeMillis()}.jpg"
                        )
                        tmp.delete()
                        if (path == null) {
                            ocrBase?.recycle()
                            withContext(Dispatchers.Main) {
                                processing = false
                                captureError = "照片保存失败，请重试"
                            }
                            return@launch
                        }
                        val recognized = runCatching {
                            val recognizer = TextRecognition.getClient(
                                ChineseTextRecognizerOptions.Builder().build()
                            )
                            if (ocrBase != null) {
                                // 第一遍：灰度+对比度拉伸
                                val enhanced = ImagePreprocessor.enhance(ocrBase)
                                val first = recognizer.process(
                                    InputImage.fromBitmap(enhanced, 0)
                                ).awaitTask()
                                enhanced.recycle()
                                // 文字过少 → 自适应二值化兜底（点阵喷码、低对比包装）
                                val result = if (first.text.ocrContentLength() < 10) {
                                    val bin = ImagePreprocessor.binarize(ocrBase)
                                    val second = recognizer.process(
                                        InputImage.fromBitmap(bin, 0)
                                    ).awaitTask()
                                    bin.recycle()
                                    if (second.text.ocrContentLength() >
                                        first.text.ocrContentLength()
                                    ) second else first
                                } else first
                                ocrBase.recycle()
                                result
                            } else {
                                // 原图解码失败兜底：直接识别压缩图
                                recognizer.process(
                                    InputImage.fromFilePath(context, Uri.fromFile(File(path)))
                                ).awaitTask()
                            }
                        }.getOrNull()
                        withContext(Dispatchers.Main) {
                            processing = false
                            photoPath = path
                            ocrResult = OcrParser.parse(recognized?.text ?: "")
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    captureError = "拍照失败：${exception.message}"
                }
            }
        )
    }

    Box(Modifier.fillMaxSize()) {
        if (hasPermission) {
            OcrCameraPreview(imageCapture)
        } else {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("需要相机权限才能拍照录入")
                TextButton(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("授予权限")
                }
            }
        }

        TextButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) { Text("返回", color = if (hasPermission) Color.White else Color.Unspecified) }

        if (hasPermission && ocrResult == null) {
            Surface(
                onClick = { if (!processing) capture() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
                    .size(72.dp),
                shape = CircleShape,
                color = Color.White,
                shadowElevation = 6.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (processing) CircularProgressIndicator(Modifier.size(32.dp))
                }
            }
        }

        if (processing) {
            Text(
                "识别中…",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0x66000000))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        ocrResult?.let { result ->
            OcrPickPanel(
                result = result,
                photoPath = photoPath,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                onConfirm = onConfirm,
                onRetake = {
                    ocrResult = null
                    photoPath = null
                },
                onCancel = onBack
            )
        }
    }

    captureError?.let { msg ->
        AlertDialog(
            onDismissRequest = { captureError = null },
            title = { Text("提示") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { captureError = null }) { Text("知道了") } }
        )
    }
}

/** 当前待填充的字段槽 */
private enum class PickField { NAME, PRODUCE, EXPIRY }

/**
 * OCR 结果点选面板：
 * 三个字段槽（名称/生产日期/到期日期）以自动推断值为初始；
 * 点击下方任一识别行即填入当前高亮槽，含日期的行点日期槽时取行内第一个日期；
 * 填充后自动跳到下一个空槽，全部填完停留在最后槽位。
 */
@Composable
private fun OcrPickPanel(
    result: OcrParser.Result,
    photoPath: String?,
    modifier: Modifier = Modifier,
    onConfirm: (ItemPrefill) -> Unit,
    onRetake: () -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf(result.name.orEmpty()) }
    var produceDate by remember { mutableLongStateOf(result.produceDate ?: 0L) }
    var expiryDate by remember { mutableLongStateOf(result.expiryDate ?: 0L) }
    var activeField by remember {
        mutableStateOf(
            when {
                result.name == null -> PickField.NAME
                result.produceDate == null -> PickField.PRODUCE
                result.expiryDate == null -> PickField.EXPIRY
                else -> PickField.NAME
            }
        )
    }

    fun advance() {
        activeField = when (activeField) {
            PickField.NAME -> if (produceDate <= 0L) PickField.PRODUCE else PickField.EXPIRY
            PickField.PRODUCE -> if (expiryDate <= 0L) PickField.EXPIRY else PickField.NAME
            PickField.EXPIRY -> if (name.isBlank()) PickField.NAME else PickField.PRODUCE
        }
    }

    fun onLineClick(line: OcrParser.Line) {
        when (activeField) {
            PickField.NAME -> {
                name = line.text.take(30)
                advance()
            }
            PickField.PRODUCE -> if (line.dates.isNotEmpty()) {
                produceDate = line.dates.first()
                advance()
            }
            PickField.EXPIRY -> if (line.dates.isNotEmpty()) {
                expiryDate = line.dates.first()
                advance()
            }
        }
    }

    Surface(
        modifier = modifier,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            Modifier
                .padding(16.dp)
                .heightIn(max = 480.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "识别结果：点击下方文字填充到高亮字段",
                style = MaterialTheme.typography.titleMedium
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                photoPath?.let { p ->
                    ImageUtils.loadBitmap(p)?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "照片",
                            modifier = Modifier.size(72.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                Column(Modifier.padding(start = 12.dp)) {
                    FieldSlot(
                        label = "名称",
                        value = name.ifBlank { "未填写" },
                        active = activeField == PickField.NAME,
                        onClick = { activeField = PickField.NAME },
                        onClear = if (name.isNotBlank()) ({ name = "" }) else null
                    )
                    FieldSlot(
                        label = "生产日期",
                        value = DateUtils.formatDate(produceDate.takeIf { it > 0 }),
                        active = activeField == PickField.PRODUCE,
                        onClick = { activeField = PickField.PRODUCE },
                        onClear = if (produceDate > 0) ({ produceDate = 0L }) else null
                    )
                    FieldSlot(
                        label = "到期日期",
                        value = DateUtils.formatDate(expiryDate.takeIf { it > 0 }),
                        active = activeField == PickField.EXPIRY,
                        onClick = { activeField = PickField.EXPIRY },
                        onClear = if (expiryDate > 0) ({ expiryDate = 0L }) else null
                    )
                }
            }

            HorizontalDivider()

            if (result.lines.isEmpty()) {
                Text(
                    "未识别到有效文字，可重拍或继续手动补全",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(result.lines) { line ->
                        Text(
                            line.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (line.dates.isNotEmpty()) MaterialTheme.colorScheme.primary
                            else Color.Unspecified,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onLineClick(line) }
                                .padding(vertical = 6.dp, horizontal = 4.dp)
                        )
                    }
                }
            }

            Button(
                onClick = {
                    onConfirm(
                        ItemPrefill(
                            name = name.ifBlank { null },
                            produceDate = produceDate,
                            expiryDate = expiryDate,
                            photoPath = photoPath
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("使用选中内容") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                OutlinedButton(onClick = onRetake) { Text("重拍") }
                TextButton(onClick = onCancel) { Text("取消") }
            }
        }
    }
}

@Composable
private fun FieldSlot(
    label: String,
    value: String,
    active: Boolean,
    onClick: () -> Unit,
    onClear: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            "$label：",
            style = MaterialTheme.typography.bodyMedium,
            color = if (active) MaterialTheme.colorScheme.primary else Color.Unspecified
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        if (onClear != null) {
            TextButton(onClick = onClear) { Text("清除", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun OcrCameraPreview(imageCapture: ImageCapture) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build()
                    .also { it.surfaceProvider = previewView.surfaceProvider }
                provider.unbindAll()
                runCatching {
                    provider.bindToLifecycle(
                        lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
                    )
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        }
    }
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
    }

/** 去除空白后的文字长度，用于判断识别结果是否过少（触发二值化兜底） */
private fun String.ocrContentLength(): Int = replace(Regex("\\s"), "").length

package com.baibaoge.home.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.baibaoge.home.util.QrCodeUtil
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * 扫码页（设计方案 3.2）：ML Kit 端侧离线识别。
 * 商品条码 → onBarcode；物品码 → onOpenItem；地点码 → onOpenLocation。
 */
@Composable
fun ScanScreen(
    onOpenItem: (String) -> Unit,
    onOpenLocation: (String) -> Unit,
    onBarcode: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
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

    var showManualDialog by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        if (hasPermission) {
            BarcodeCameraPreview(onDetected = { content ->
                val parsed = QrCodeUtil.parse(content)
                when (parsed?.first) {
                    QrCodeUtil.TYPE_ITEM -> onOpenItem(parsed.second)
                    QrCodeUtil.TYPE_LOCATION -> onOpenLocation(parsed.second)
                    else -> onBarcode(content)
                }
            })
            Text(
                "将条码/二维码放入取景框内，自动识别",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0x66000000))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        } else {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("需要相机权限才能扫码")
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

        TextButton(
            onClick = { showManualDialog = true },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
        ) {
            Text(
                "扫码失败？手动输入条码",
                color = if (hasPermission) Color.White else Color.Unspecified
            )
        }
    }

    // 扫码失败兜底：手动输入条码编号
    if (showManualDialog) {
        var code by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showManualDialog = false },
            title = { Text("手动输入条码") },
            text = {
                OutlinedTextField(
                    value = code, onValueChange = { code = it },
                    label = { Text("商品条码") }, singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = code.isNotBlank(),
                    onClick = {
                        showManualDialog = false
                        onBarcode(code.trim())
                    }
                ) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showManualDialog = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun BarcodeCameraPreview(onDetected: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    var handled by remember { mutableStateOf(false) }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val scanner = BarcodeScanning.getClient()
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build()
                    .also { it.surfaceProvider = previewView.surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(analyzerExecutor) { proxy ->
                    val mediaImage = proxy.image
                    if (mediaImage == null || handled) {
                        proxy.close()
                        return@setAnalyzer
                    }
                    scanner
                        .process(InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees))
                        .addOnSuccessListener { barcodes ->
                            val value = barcodes.firstOrNull()?.rawValue
                            if (!handled && !value.isNullOrBlank()) {
                                handled = true
                                ContextCompat.getMainExecutor(ctx).execute { onDetected(value) }
                            }
                        }
                        .addOnCompleteListener { proxy.close() }
                }
                provider.unbindAll()
                runCatching {
                    provider.bindToLifecycle(
                        lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
                    )
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            analyzerExecutor.shutdown()
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        }
    }
}

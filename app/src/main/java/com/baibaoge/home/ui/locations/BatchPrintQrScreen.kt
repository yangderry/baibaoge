package com.baibaoge.home.ui.locations

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.baibaoge.home.auth.AuthManager
import com.baibaoge.home.data.AppDatabase
import com.baibaoge.home.util.ImageUtils
import com.baibaoge.home.util.QrPrintPdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 批量打印地点二维码页：
 * 勾选地点 → 生成 A4 标签 PDF（每页 12 个）→
 * 系统打印对话框（可选局域网打印机或另存为 PDF），或经 SAF 直接保存 PDF 到手机。
 */
@Composable
fun BatchPrintQrScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val locationDao = remember { db.locationDao() }
    val auth = remember { AuthManager.getInstance(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val locations by locationDao.observeAll().collectAsState(initial = emptyList())
    var selected by remember { mutableStateOf(setOf<String>()) }
    var working by remember { mutableStateOf(false) }

    // SAF 保存 PDF：无需存储权限，用户自选位置
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        auth.externalActivityInFlight = false
        if (uri != null) {
            working = true
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    val pdf = QrPrintPdf.build(
                        context, locations.filter { it.locationId in selected }
                    ) ?: return@withContext false
                    val written = runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            pdf.inputStream().use { it.copyTo(out) }
                        } != null
                    }.getOrDefault(false)
                    pdf.delete()
                    written
                }
                working = false
                snackbarHostState.showSnackbar(if (ok) "PDF 已保存" else "保存失败")
            }
        }
    }

    /** 系统打印：PrintManager 弹出打印对话框，可选局域网打印机或另存为 PDF */
    fun startPrint() {
        working = true
        scope.launch {
            val pdf = withContext(Dispatchers.IO) {
                QrPrintPdf.build(context, locations.filter { it.locationId in selected })
            }
            working = false
            if (pdf == null) {
                snackbarHostState.showSnackbar("PDF 生成失败")
                return@launch
            }
            auth.externalActivityInFlight = true
            val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            pm.print("百宝格地点二维码", object : PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: PrintAttributes?, newAttributes: PrintAttributes,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback, extras: Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback.onLayoutCancelled()
                        return
                    }
                    callback.onLayoutFinished(
                        PrintDocumentInfo.Builder(pdf.name)
                            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                            .build(), true
                    )
                }

                override fun onWrite(
                    pages: Array<out PageRange>?, destination: ParcelFileDescriptor,
                    cancellationSignal: CancellationSignal?, callback: WriteResultCallback
                ) {
                    runCatching {
                        pdf.inputStream().use { input ->
                            FileOutputStream(destination.fileDescriptor).use { input.copyTo(it) }
                        }
                    }.onSuccess {
                        callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    }.onFailure {
                        callback.onWriteFailed(it.message)
                    }
                }

                override fun onFinish() {
                    auth.externalActivityInFlight = false
                    pdf.delete()
                }
            }, null)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onBack) { Text("返回") }
        Text(
            "批量打印地点二维码",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "已选 ${selected.size} / ${locations.size}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = {
                selected =
                    if (selected.size == locations.size) emptySet()
                    else locations.map { it.locationId }.toSet()
            }) {
                Text(if (selected.size == locations.size) "全不选" else "全选")
            }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(locations, key = { it.locationId }) { loc ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = loc.locationId in selected,
                        onCheckedChange = { checked ->
                            selected =
                                if (checked) selected + loc.locationId
                                else selected - loc.locationId
                        }
                    )
                    ImageUtils.loadBitmap(loc.qrPath)?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(36.dp)
                        )
                    }
                    Text(loc.locationName, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    auth.externalActivityInFlight = true
                    saveLauncher.launch("百宝格地点二维码.pdf")
                },
                enabled = selected.isNotEmpty() && !working,
                modifier = Modifier.weight(1f)
            ) { Text("保存为 PDF") }
            Button(
                onClick = { startPrint() },
                enabled = selected.isNotEmpty() && !working,
                modifier = Modifier.weight(1f)
            ) {
                if (working) CircularProgressIndicator(Modifier.size(20.dp))
                else Text("打印")
            }
        }
    }

    SnackbarHost(snackbarHostState)
}

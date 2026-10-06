package com.baibaoge.home.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.baibaoge.home.auth.AuthManager
import com.baibaoge.home.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 通用照片选择器：预览区 + 拍照 / 相册 / 移除 按钮。
 * 拍照与相册均经 ImageUtils.compressToPrivate 压缩后存入 APP 私有目录。
 */
@Composable
fun PhotoPicker(
    photoPath: String?,
    onPhotoChange: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = remember { AuthManager.getInstance(context) }
    var processing by remember { mutableStateOf(false) }
    var cameraFile by remember { mutableStateOf<File?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        auth.externalActivityInFlight = false
        val file = cameraFile
        if (success && file != null && file.exists()) {
            processing = true
            scope.launch(Dispatchers.IO) {
                val path = ImageUtils.compressToPrivate(
                    context, file, "photo_${System.currentTimeMillis()}.jpg"
                )
                withContext(Dispatchers.Main) {
                    processing = false
                    onPhotoChange(path)
                }
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        auth.externalActivityInFlight = false
        uri?.let {
            processing = true
            scope.launch(Dispatchers.IO) {
                val path = ImageUtils.compressToPrivate(
                    context, uri, "photo_${System.currentTimeMillis()}.jpg"
                )
                withContext(Dispatchers.Main) {
                    processing = false
                    onPhotoChange(path)
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            when {
                processing -> CircularProgressIndicator()
                !photoPath.isNullOrBlank() -> AsyncImageBox(
                    path = photoPath,
                    contentDescription = "照片预览",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                )
                else -> Text(
                    "点击拍照或选择相册",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedButton(onClick = {
                val tmp = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
                cameraFile = tmp
                val uri = FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", tmp
                )
                auth.externalActivityInFlight = true
                cameraLauncher.launch(uri)
            }) { Text("拍照") }
            OutlinedButton(onClick = {
                auth.externalActivityInFlight = true
                galleryLauncher.launch("image/*")
            }) { Text("相册") }
            if (!photoPath.isNullOrBlank()) {
                TextButton(onClick = { onPhotoChange(null) }) { Text("移除") }
            }
        }
    }
}

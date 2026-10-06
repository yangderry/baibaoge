package com.baibaoge.home.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

/** 照片处理（设计方案 3.2：长边 1280px、JPEG 质量 80%、保存至 APP 私有目录） */
object ImageUtils {
    const val MAX_EDGE = 1280
    const val JPEG_QUALITY = 80

    /** 压缩 srcFile（含 EXIF 方向纠正）并保存到私有目录 photos/destName，返回绝对路径；失败返回 null */
    fun compressToPrivate(context: Context, srcFile: File, destName: String): String? {
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(srcFile.absolutePath, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
            var bmp = BitmapFactory.decodeFile(
                srcFile.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sample }
            ) ?: return null

            // EXIF 方向纠正
            val exif = ExifInterface(srcFile.absolutePath)
            val degrees = when (
                exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees != 0f) {
                val rotated = Bitmap.createBitmap(
                    bmp, 0, 0, bmp.width, bmp.height,
                    Matrix().apply { postRotate(degrees) }, true
                )
                if (rotated != bmp) bmp.recycle()
                bmp = rotated
            }

            // 长边缩至 1280
            val longEdge = maxOf(bmp.width, bmp.height)
            if (longEdge > MAX_EDGE) {
                val scale = MAX_EDGE.toFloat() / longEdge
                val scaled = Bitmap.createBitmap(
                    bmp, 0, 0, bmp.width, bmp.height,
                    Matrix().apply { postScale(scale, scale) }, true
                )
                if (scaled != bmp) bmp.recycle()
                bmp = scaled
            }

            val dir = File(context.filesDir, "photos").apply { mkdirs() }
            val out = File(dir, destName)
            FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            bmp.recycle()
            out.absolutePath
        }.getOrNull()
    }

    /** 从相册 content Uri 压缩保存到私有目录（先拷贝到缓存再复用文件压缩流程）；失败返回 null */
    fun compressToPrivate(context: Context, uri: Uri, destName: String): String? {
        return runCatching {
            val tmp = File(context.cacheDir, "pick_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tmp).use { output -> input.copyTo(output) }
            } ?: return null
            val out = compressToPrivate(context, tmp, destName)
            tmp.delete()
            out
        }.getOrNull()
    }

    /** 加载本地图片文件为 Bitmap；文件不存在或解码失败返回 null */
    fun loadBitmap(path: String?): Bitmap? =
        path?.takeIf { it.isNotBlank() }
            ?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
}

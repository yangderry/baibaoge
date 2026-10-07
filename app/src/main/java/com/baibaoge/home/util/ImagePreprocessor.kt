package com.baibaoge.home.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * OCR 图像预处理（保守增强路线，不更换 ML Kit 引擎）：
 * 1. 原图高分辨率解码（≤2000px，保留小字细节；展示存储仍走 1280px 压缩）
 * 2. 灰度化 + 对比度拉伸（1%~99% 直方图截断），应对反光/弱光
 * 3. 自适应二值化（局部均值阈值，积分图加速），兜底点阵喷码/低对比场景
 */
object ImagePreprocessor {

    /** OCR 用原图长边上限；兼顾细节与内存 */
    const val OCR_MAX_EDGE = 2000

    /** 解码拍照原图用于 OCR：限制长边 maxEdge 并做 EXIF 方向纠正；失败返回 null */
    fun decodeForOcr(file: File, maxEdge: Int = OCR_MAX_EDGE): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
        var bmp = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return null

        val degrees = when (
            ExifInterface(file.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
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
        bmp
    }.getOrNull()

    /** 灰度化 + 对比度拉伸：返回新 Bitmap（调用方负责回收），不修改原图 */
    fun enhance(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        val gray = IntArray(w * h)
        val hist = IntArray(256)
        for (i in pixels.indices) {
            val y = luminance(pixels[i])
            gray[i] = y
            hist[y]++
        }

        // 1% / 99% 分位截断后线性拉伸到全灰阶
        val total = w * h
        val lo = percentile(hist, total, 0.01f)
        val hi = percentile(hist, total, 0.99f)
        val range = max(hi - lo, 1)

        val out = IntArray(w * h)
        for (i in gray.indices) {
            val v = ((gray[i] - lo) * 255 / range).coerceIn(0, 255)
            out[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * 自适应二值化：每个像素与局部窗口均值比较，暗于均值-offset 判为黑。
     * 对光照不均的包装表面比全局阈值稳健；输入要求已由 [decodeForOcr] 限制尺寸。
     * 返回新 Bitmap（调用方负责回收），不修改原图。
     */
    fun binarize(src: Bitmap, window: Int = 31, offset: Int = 12): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        // 灰度积分图：integral[y+1][x+1] = 矩形 (0,0)-(x,y) 灰度和
        val stride = w + 1
        val integral = LongArray(stride * (h + 1))
        for (y in 0 until h) {
            var rowSum = 0L
            val srcRow = y * w
            val dstRow = (y + 1) * stride
            val prevRow = y * stride
            for (x in 0 until w) {
                rowSum += luminance(pixels[srcRow + x])
                integral[dstRow + x + 1] = integral[prevRow + x + 1] + rowSum
            }
        }

        val half = window / 2
        val out = IntArray(w * h)
        for (y in 0 until h) {
            val y1 = max(0, y - half)
            val y2 = min(h - 1, y + half)
            for (x in 0 until w) {
                val x1 = max(0, x - half)
                val x2 = min(w - 1, x + half)
                val count = (y2 - y1 + 1) * (x2 - x1 + 1)
                val sum = integral[(y2 + 1) * stride + x2 + 1] -
                        integral[y1 * stride + x2 + 1] -
                        integral[(y2 + 1) * stride + x1] +
                        integral[y1 * stride + x1]
                val mean = sum / count
                val v = if (luminance(pixels[y * w + x]) > mean - offset) 255 else 0
                out[y * w + x] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Rec.601 亮度 */
    private fun luminance(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    private fun percentile(hist: IntArray, total: Int, ratio: Float): Int {
        val target = (total * ratio).toInt()
        var acc = 0
        for (i in hist.indices) {
            acc += hist[i]
            if (acc >= target) return i
        }
        return 255
    }
}

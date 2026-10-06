package com.baibaoge.home.util

import android.content.Context
import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import java.io.File
import java.io.FileOutputStream

/**
 * 二维码工具（设计方案 3.2）。
 * 内容格式：物品码 BBG:ITEM:{itemId}；地点码 BBG:LOC:{locationId}。
 */
object QrCodeUtil {
    const val PREFIX_ITEM = "BBG:ITEM:"
    const val PREFIX_LOC = "BBG:LOC:"

    const val TYPE_ITEM = "ITEM"
    const val TYPE_LOCATION = "LOC"

    fun itemContent(itemId: String) = PREFIX_ITEM + itemId
    fun locationContent(locationId: String) = PREFIX_LOC + locationId

    /** 解析自定义二维码，返回 (TYPE_ITEM/TYPE_LOCATION, id)；商品条码等返回 null */
    fun parse(content: String): Pair<String, String>? = when {
        content.startsWith(PREFIX_ITEM) ->
            TYPE_ITEM to content.removePrefix(PREFIX_ITEM)
        content.startsWith(PREFIX_LOC) ->
            TYPE_LOCATION to content.removePrefix(PREFIX_LOC)
        else -> null
    }

    /** 生成二维码 PNG 保存到私有目录 qrs/，返回绝对路径；失败返回 null */
    fun generateToFile(context: Context, content: String, fileName: String, sizePx: Int = 512): String? {
        return runCatching {
            val matrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    bmp.setPixel(x, y, if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
                }
            }
            val dir = File(context.filesDir, "qrs").apply { mkdirs() }
            val file = File(dir, fileName)
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()
            file.absolutePath
        }.getOrNull()
    }
}

package com.baibaoge.home.util

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.baibaoge.home.data.entity.LocationEntity
import java.io.File
import java.io.FileOutputStream

/**
 * 地点二维码批量打印 PDF（设计方案 3.2）：
 * A4 纵向，每页 3 列 x 4 行共 12 个标签，标准标签排版：
 * 每个标签 = 裁切边框 + 居中二维码 + 下方地点名称（过长自动省略）。
 */
object QrPrintPdf {

    private const val PAGE_W = 595 // A4 @72dpi
    private const val PAGE_H = 842
    private const val MARGIN = 24
    private const val COLS = 3
    private const val ROWS = 4
    private const val PER_PAGE = COLS * ROWS

    /** 文字超过最大宽度时尾部省略 */
    private fun ellipsized(paint: Paint, text: String, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var s = text
        while (s.length > 1 && paint.measureText("$s…") > maxWidth) s = s.dropLast(1)
        return "$s…"
    }

    /** 生成 PDF 到缓存目录，返回文件；失败返回 null。调用方用完后自行删除。 */
    fun build(context: Context, locations: List<LocationEntity>): File? {
        if (locations.isEmpty()) return null
        return runCatching {
            val doc = PdfDocument()
            val cellW = (PAGE_W - MARGIN * 2) / COLS
            val cellH = (PAGE_H - MARGIN * 2) / ROWS
            val qrSize = minOf(cellW - 16f, cellH - 40f) // 上下留白 + 底部名称行
            val textPaint = Paint().apply {
                textSize = 12f
                color = Color.BLACK
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            val borderPaint = Paint().apply {
                style = Paint.Style.STROKE
                color = Color.LTGRAY
                strokeWidth = 0.8f
                isAntiAlias = true
            }
            locations.chunked(PER_PAGE).forEachIndexed { pageIdx, chunk ->
                val page = doc.startPage(
                    PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageIdx + 1).create()
                )
                val canvas = page.canvas
                chunk.forEachIndexed { i, loc ->
                    val col = i % COLS
                    val row = i / COLS
                    val cellLeft = MARGIN + col * cellW
                    val cellTop = MARGIN + row * cellH
                    val qrLeft = cellLeft + (cellW - qrSize) / 2f
                    val qrTop = cellTop + 8f

                    // 裁切边框（标签轮廓，便于沿线裁剪）
                    canvas.drawRoundRect(
                        RectF(cellLeft + 3f, cellTop + 3f, cellLeft + cellW - 3f, cellTop + cellH - 3f),
                        4f, 4f, borderPaint
                    )

                    QrCodeUtil.generateBitmap(QrCodeUtil.locationContent(loc.locationId))?.let { bmp ->
                        canvas.drawBitmap(
                            bmp, null,
                            RectF(qrLeft, qrTop, qrLeft + qrSize, qrTop + qrSize),
                            null
                        )
                        bmp.recycle()
                    }
                    canvas.drawText(
                        ellipsized(textPaint, loc.locationName, cellW - 12f),
                        cellLeft + cellW / 2f,
                        qrTop + qrSize + 18f,
                        textPaint
                    )
                }
                doc.finishPage(page)
            }
            val out = File(context.cacheDir, "loc_qr_${System.currentTimeMillis()}.pdf")
            FileOutputStream(out).use { doc.writeTo(it) }
            doc.close()
            out
        }.getOrNull()
    }
}

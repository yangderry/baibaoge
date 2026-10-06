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
 * A4 纵向，每页 3 列 x 4 行共 12 个标签，每个标签 = 二维码 + 地点名称。
 */
object QrPrintPdf {

    private const val PAGE_W = 595 // A4 @72dpi
    private const val PAGE_H = 842
    private const val MARGIN = 24
    private const val COLS = 3
    private const val ROWS = 4
    private const val PER_PAGE = COLS * ROWS

    /** 生成 PDF 到缓存目录，返回文件；失败返回 null。调用方用完后自行删除。 */
    fun build(context: Context, locations: List<LocationEntity>): File? {
        if (locations.isEmpty()) return null
        return runCatching {
            val doc = PdfDocument()
            val cellW = (PAGE_W - MARGIN * 2) / COLS
            val cellH = (PAGE_H - MARGIN * 2) / ROWS
            val qrSize = minOf(cellW, cellH - 28) // 底部留名称文字空间
            val textPaint = Paint().apply {
                textSize = 11f
                color = Color.BLACK
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            locations.chunked(PER_PAGE).forEachIndexed { pageIdx, chunk ->
                val page = doc.startPage(
                    PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageIdx + 1).create()
                )
                chunk.forEachIndexed { i, loc ->
                    val col = i % COLS
                    val row = i / COLS
                    val left = MARGIN + col * cellW + (cellW - qrSize) / 2f
                    val top = MARGIN + row * cellH + 4f
                    QrCodeUtil.generateBitmap(QrCodeUtil.locationContent(loc.locationId))?.let { bmp ->
                        page.canvas.drawBitmap(bmp, null, RectF(left, top, left + qrSize, top + qrSize), null)
                        bmp.recycle()
                    }
                    page.canvas.drawText(
                        loc.locationName, left + qrSize / 2f, top + qrSize + 16f, textPaint
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

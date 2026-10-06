package com.baibaoge.home.util

import java.util.Calendar

/**
 * OCR 文本解析（设计方案 3.2）：
 * 保留全部识别行供用户点选填充；同时给出自动推断的名称/生产日期/到期日期作为初始值。
 * 解析结果仅供用户确认后使用，不直接写库。
 */
object OcrParser {

    /** 一行识别文字及其内含的日期（可能多个） */
    data class Line(val text: String, val dates: List<Long>)

    data class Result(
        val name: String?,
        val produceDate: Long?,
        val expiryDate: Long?,
        /** 全部非空识别行，供用户点选填充 */
        val lines: List<Line>
    )

    /** 支持 2024-01-02 / 2024/01/02 / 2024.01.02 / 2024年1月2日 */
    private val DATE_REGEX = Regex(
        "(20\\d{2})\\s*[年./\\-]\\s*(0?[1-9]|1[0-2])\\s*[月./\\-]\\s*(0?[1-9]|[12]\\d|3[01])\\s*日?"
    )

    private val PRODUCE_KEYS = listOf("生产日期", "制造日期", "生产批号", "生产", "MFG", "mfg")
    private val EXPIRY_KEYS = listOf(
        "有效期至", "保质期至", "失效日期", "此日期前", "最佳食用", "限期使用",
        "到期", "有效期", "保质期", "EXP", "exp"
    )

    fun parse(text: String): Result {
        var produce: Long? = null
        var expiry: Long? = null
        val bareDates = mutableListOf<Long>()
        val nameCandidates = mutableListOf<String>()
        val lines = mutableListOf<Line>()

        text.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            val dates = DATE_REGEX.findAll(line).map { toMillis(it.groupValues) }.toList()
            lines += Line(line, dates)
            when {
                dates.isNotEmpty() && PRODUCE_KEYS.any { line.contains(it) } -> {
                    if (produce == null) produce = dates.first()
                }
                dates.isNotEmpty() && EXPIRY_KEYS.any { line.contains(it) } -> {
                    if (expiry == null) expiry = dates.first()
                }
                else -> bareDates += dates
            }
            // 名称候选：不含日期、含中文、长度>=2，且不是纯关键词行
            if (dates.isEmpty() && line.length >= 2 &&
                line.any { it in '一'..'鿿' } &&
                PRODUCE_KEYS.none { line.contains(it) } &&
                EXPIRY_KEYS.none { line.contains(it) }
            ) {
                nameCandidates += line
            }
        }

        // 无关键词标注的裸日期兜底：第一个→生产日期，第二个→到期日期
        val bare = bareDates.toMutableList()
        if (produce == null && bare.isNotEmpty()) produce = bare.removeAt(0)
        if (expiry == null && bare.isNotEmpty()) expiry = bare.removeAt(0)

        val name = nameCandidates.maxByOrNull { it.length }?.take(30)
        return Result(name, produce, expiry, lines)
    }

    private fun toMillis(groups: List<String>): Long {
        val cal = Calendar.getInstance()
        cal.set(groups[1].toInt(), groups[2].toInt() - 1, groups[3].toInt(), 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}

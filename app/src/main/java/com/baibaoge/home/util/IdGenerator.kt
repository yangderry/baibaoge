package com.baibaoge.home.util

/** ID 生成规则（设计方案 4.2） */
object IdGenerator {

    /**
     * 物品 ID：类型前缀(F=食品/M=药品/C=衣物/O=其他) + yyyyMMddHHmmss + 2位随机数
     */
    fun itemId(itemType: Int): String {
        val prefix = when (itemType) {
            1 -> "F"
            2 -> "M"
            3 -> "C"
            else -> "O"
        }
        val time = java.text.SimpleDateFormat("yyyyMMddHHmmss", java.util.Locale.US)
            .format(java.util.Date())
        val rand = (10..99).random()
        return "$prefix$time$rand"
    }

    /** 地点 ID：L + 毫秒时间戳 + 2位随机数 */
    fun locationId(): String {
        val rand = (10..99).random()
        return "L${System.currentTimeMillis()}$rand"
    }
}

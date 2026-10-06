package com.baibaoge.home.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateUtils {
    private val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /** 毫秒时间戳 → yyyy-MM-dd；null 或 <=0 返回 "未设置" */
    fun formatDate(time: Long?): String =
        if (time == null || time <= 0) "未设置" else fmt.format(Date(time))
}

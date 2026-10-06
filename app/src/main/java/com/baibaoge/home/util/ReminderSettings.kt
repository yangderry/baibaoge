package com.baibaoge.home.util

import android.content.Context
import com.baibaoge.home.data.entity.ItemEntity

/**
 * 临期提醒天数配置（设计方案 3.1.4）：
 * 食品默认 7 天、药品默认 30 天、其他默认 15 天；衣物归入"其他"。
 * SharedPreferences 持久化，本地计算无需联网。
 */
object ReminderSettings {
    private const val PREF = "reminder_settings"
    private const val KEY_FOOD = "food_days"
    private const val KEY_MEDICINE = "medicine_days"
    private const val KEY_OTHER = "other_days"

    const val DEFAULT_FOOD = 7
    const val DEFAULT_MEDICINE = 30
    const val DEFAULT_OTHER = 15

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun foodDays(context: Context) = prefs(context).getInt(KEY_FOOD, DEFAULT_FOOD)
    fun medicineDays(context: Context) = prefs(context).getInt(KEY_MEDICINE, DEFAULT_MEDICINE)
    fun otherDays(context: Context) = prefs(context).getInt(KEY_OTHER, DEFAULT_OTHER)

    fun setFoodDays(context: Context, days: Int) =
        prefs(context).edit().putInt(KEY_FOOD, days).apply()

    fun setMedicineDays(context: Context, days: Int) =
        prefs(context).edit().putInt(KEY_MEDICINE, days).apply()

    fun setOtherDays(context: Context, days: Int) =
        prefs(context).edit().putInt(KEY_OTHER, days).apply()

    /** 该物品类型的提前提醒天数（衣物归入"其他"） */
    fun thresholdFor(context: Context, itemType: Int): Int = when (itemType) {
        ItemEntity.TYPE_FOOD -> foodDays(context)
        ItemEntity.TYPE_MEDICINE -> medicineDays(context)
        else -> otherDays(context)
    }

    /** 距到期剩余天数（按自然日 0 点对齐；负数=已过期） */
    fun daysUntil(expiryMs: Long): Int {
        val now = System.currentTimeMillis()
        val dayMs = 24L * 60 * 60 * 1000
        return ((expiryMs - now) / dayMs).toInt().let {
            // 跨天边界：到期时刻早于现在但不足一天仍算 0 天
            if (it < 0 && expiryMs - now > -dayMs) 0 else it
        }
    }
}

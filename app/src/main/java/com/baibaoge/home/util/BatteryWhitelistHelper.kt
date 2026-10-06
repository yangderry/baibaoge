package com.baibaoge.home.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * 厂商电池白名单引导（设计方案 3.1.4）：
 * 避免因电池优化/后台清理导致 WorkManager 每日提醒被杀死。
 * 兼容华为/小米/OPPO/vivo/Samsung/OnePlus 等常见国产机。
 */
object BatteryWhitelistHelper {

    /** 当前应用是否在电池优化白名单（Android 6+） */
    fun isWhitelisted(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** 跳转到系统电池优化设置页（通用方案） */
    fun openBatterySettings(context: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        runCatching { context.startActivity(intent) }
    }

    /** 尝试跳转各厂商自管后台设置页（三星/华为/小米/OPPO/vivo/一加等） */
    fun openVendorSettings(context: Context) {
        val pkg = context.packageName
        val vendorActions = listOf(
            // Samsung (One UI)
            Intent().setClassName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.battery.ui.BatteryActivity"
            ),
            // Huawei / Honor (EMUI)
            Intent().setClassName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.process.ProtectActivity"
            ),
            Intent().setClassName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"
            ),
            // Xiaomi (MIUI)
            Intent().setClassName(
                "com.miui.powerkeeper",
                "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
            ),
            Intent("miui.intent.action.POWER_HIDE_MODE_APP_LIST"),
            // OPPO (ColorOS)
            Intent().setClassName(
                "com.coloros.oppoguardelf",
                "com.coloros.powermanager.fuelgaue.PowerConsumptionActivity"
            ),
            // vivo (FuntouchOS / OriginOS)
            Intent().setClassName(
                "com.vivo.abe",
                "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity"
            ),
            Intent().setClassName(
                "com.iqoo.powersaving",
                "com.iqoo.powersaving.PowerSavingManagerActivity"
            ),
            // OnePlus (OxygenOS)
            Intent().setClassName(
                "com.oneplus.security",
                "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
            ),
        )
        vendorActions.firstOrNull { resolve(context, it) }?.let { context.startActivity(it) }
            ?: openBatterySettings(context)
    }

    private fun resolve(context: Context, intent: Intent): Boolean =
        runCatching {
            intent.resolveActivity(context.packageManager) != null
        }.getOrDefault(false)
}

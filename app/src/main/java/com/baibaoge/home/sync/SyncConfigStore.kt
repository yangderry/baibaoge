package com.baibaoge.home.sync

import android.content.Context

/**
 * 同步配置（设计方案 3.5）：
 * NAS 局域网 IP、端口（默认 5050，NAS 宿主机 5000 被 apache-dav 占用）、家庭 WiFi SSID 白名单（逗号分隔，可多个）。
 * SharedPreferences 持久化。
 */
object SyncConfigStore {
    private const val PREF = "sync_config"
    private const val KEY_IP = "nas_ip"
    private const val KEY_PORT = "nas_port"
    private const val KEY_SSIDS = "ssid_whitelist"

    const val DEFAULT_PORT = 5050

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun ip(context: Context) = prefs(context).getString(KEY_IP, "") ?: ""

    fun port(context: Context) = prefs(context).getInt(KEY_PORT, DEFAULT_PORT)

    fun setIp(context: Context, value: String) =
        prefs(context).edit().putString(KEY_IP, value.trim()).apply()

    fun setPort(context: Context, value: Int) =
        prefs(context).edit().putInt(KEY_PORT, value).apply()

    /** SSID 白名单，逗号分隔存储；返回去除引号和空白的列表 */
    fun ssids(context: Context): List<String> =
        (prefs(context).getString(KEY_SSIDS, "") ?: "")
            .split(',', '，')
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotBlank() }

    fun setSsidsRaw(context: Context, raw: String) =
        prefs(context).edit().putString(KEY_SSIDS, raw).apply()

    fun ssidsRaw(context: Context) = prefs(context).getString(KEY_SSIDS, "") ?: ""
}

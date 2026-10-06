package com.baibaoge.home.sync

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * 网络状态检测（设计方案 3.5）：
 * 区分 WiFi / 移动网络，读取当前 WiFi SSID（Android 10+ 需要定位权限）。
 */
object NetworkUtils {

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    fun isWifiConnected(context: Context): Boolean = capabilities(context)
        ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

    fun isCellular(context: Context): Boolean = capabilities(context)
        ?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true

    /**
     * 当前连接的 WiFi SSID；非 WiFi、无定位权限或系统未暴露时返回 null。
     */
    @Suppress("DEPRECATION")
    fun currentSsid(context: Context): String? {
        if (!hasLocationPermission(context)) return null
        val caps = capabilities(context) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null

        var ssid: String? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            (caps.transportInfo as? WifiInfo)?.ssid?.let { ssid = it }
        }
        if (ssid == null) {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            ssid = wm.connectionInfo?.ssid
        }
        val cleaned = ssid?.removeSurrounding("\"")?.trim()
        return cleaned?.takeIf {
            it.isNotBlank() && it != WifiManager.UNKNOWN_SSID && it != "0x"
        }
    }

    private fun capabilities(context: Context): NetworkCapabilities? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return null
        return cm.getNetworkCapabilities(network)
    }
}

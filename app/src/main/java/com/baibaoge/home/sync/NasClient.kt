package com.baibaoge.home.sync

import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** NAS 最新备份元数据 */
data class LatestInfo(
    val filename: String,
    val size: Long,
    val md5: String,
    val createdAt: Long,
    val createdAtIso: String
)

/**
 * NAS Flask 备份服务 HTTP 客户端（server/app.py 契约）：
 * - GET  /health         健康检查
 * - GET  /latest-version 最新备份元数据（404 表示暂无备份）
 * - POST /upload         multipart 上传，表单字段 file + md5，服务端校验失败返回 400
 * - GET  /download       下载最新备份（/download/<filename> 指定文件）
 */
object NasClient {

    private fun connect(ip: String, port: Int, path: String, method: String): HttpURLConnection {
        val conn = URL("http", ip, port, path).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 8_000
        conn.readTimeout = 60_000
        return conn
    }

    private fun HttpURLConnection.bodyString(): String =
        inputStream.bufferedReader().use { it.readText() }

    private fun HttpURLConnection.errorBodyString(): String =
        runCatching { errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull() ?: ""

    /** 健康检查；连接失败抛异常 */
    fun health(ip: String, port: Int) {
        val conn = connect(ip, port, "/health", "GET")
        try {
            if (conn.responseCode != 200) throw IllegalStateException("服务异常(${conn.responseCode})")
        } finally {
            conn.disconnect()
        }
    }

    /** 查询最新备份元数据；NAS 上无备份返回 null；连接失败抛异常 */
    fun latestVersion(ip: String, port: Int): LatestInfo? {
        val conn = connect(ip, port, "/latest-version", "GET")
        try {
            return when (conn.responseCode) {
                200 -> JSONObject(conn.bodyString()).let {
                    LatestInfo(
                        filename = it.getString("filename"),
                        size = it.getLong("size"),
                        md5 = it.getString("md5"),
                        createdAt = it.getLong("created_at"),
                        createdAtIso = it.optString("created_at_iso")
                    )
                }
                404 -> null
                else -> throw IllegalStateException("查询失败(${conn.responseCode})")
            }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * multipart 上传备份包；服务端 MD5 校验失败（400）或网络错误抛异常。
     */
    fun upload(ip: String, port: Int, file: File, md5: String) {
        val boundary = "----bbg${System.currentTimeMillis()}"
        val conn = connect(ip, port, "/upload", "POST").apply {
            doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        try {
            DataOutputStream(conn.outputStream).use { out ->
                fun field(name: String, value: String) {
                    out.writeBytes("--$boundary\r\n")
                    out.writeBytes("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
                    out.writeBytes(value)
                    out.writeBytes("\r\n")
                }
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"\r\n")
                out.writeBytes("Content-Type: application/octet-stream\r\n\r\n")
                file.inputStream().use { it.copyTo(out, 64 * 1024) }
                out.writeBytes("\r\n")
                field("md5", md5)
                out.writeBytes("--$boundary--\r\n")
            }
            if (conn.responseCode != 200) {
                val error = runCatching { JSONObject(conn.errorBodyString()).optString("error") }
                    .getOrNull().takeUnless { it.isNullOrBlank() }
                throw IllegalStateException(error ?: "上传失败(${conn.responseCode})")
            }
        } finally {
            conn.disconnect()
        }
    }

    /** 下载备份到 dest；非 200 抛异常 */
    fun download(ip: String, port: Int, filename: String?, dest: File) {
        val path = if (filename.isNullOrBlank()) "/download" else "/download/$filename"
        val conn = connect(ip, port, path, "GET")
        try {
            if (conn.responseCode != 200) throw IllegalStateException("下载失败(${conn.responseCode})")
            FileOutputStream(dest).use { out ->
                conn.inputStream.use { it.copyTo(out, 64 * 1024) }
            }
        } finally {
            conn.disconnect()
        }
    }
}

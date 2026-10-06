package com.baibaoge.home.sync

import android.content.Context
import com.baibaoge.home.data.AppDatabase
import com.baibaoge.home.data.entity.SyncLogEntity
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 同步管理（设计方案 3.5 / 同步规则表）：
 * - 同步单位：完整 zip 备份包 = SQLite 数据库 + photos 目录（含 qrs 二维码目录）
 * - 版本号：毫秒时间戳，作为文件名与日志 file_version
 * - 新版本覆盖旧版本，不做增量合并；失败仅记日志，不影响本地使用
 */
object SyncManager {
    const val DB_NAME = "baobaoge_db"
    const val TYPE_AUTO = 1
    const val TYPE_MANUAL = 2

    private const val DIR_PHOTOS = "photos"
    private const val DIR_QRS = "qrs"

    /** 手动/自动同步：打包 → 上传 NAS → 写日志；返回结果消息 */
    suspend fun syncNow(context: Context, syncType: Int): String = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val ip = SyncConfigStore.ip(app)
        val port = SyncConfigStore.port(app)
        if (ip.isBlank()) {
            recordLog(app, syncType, "upload", "failed", null, "未配置NAS地址")
            return@withContext "同步失败：未配置NAS地址"
        }
        try {
            val (zip, version) = packBackup(app)
            try {
                NasClient.upload(ip, port, zip, fileMd5(zip))
                recordLog(app, syncType, "upload", "success", version.toString(), null)
                "同步成功：版本 $version"
            } finally {
                zip.delete()
            }
        } catch (e: Exception) {
            recordLog(app, syncType, "upload", "failed", null, e.message)
            "同步失败：${e.message ?: "网络错误"}"
        }
    }

    /** 查询 NAS 最新备份信息；返回给 UI 展示的描述，异常转错误文案 */
    suspend fun fetchLatestInfo(context: Context): Pair<LatestInfo?, String?> =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val ip = SyncConfigStore.ip(app)
            if (ip.isBlank()) return@withContext null to "未配置NAS地址"
            try {
                NasClient.latestVersion(ip, SyncConfigStore.port(app)) to null
            } catch (e: Exception) {
                null to (e.message ?: "连接失败")
            }
        }

    /**
     * 从 NAS 恢复最新备份：下载 → MD5 校验 → 关库换文件 → 解压照片。
     * 返回 (是否成功需要重启, 结果消息)。
     */
    suspend fun restoreLatest(context: Context): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val ip = SyncConfigStore.ip(app)
        val port = SyncConfigStore.port(app)
        if (ip.isBlank()) {
            recordLog(app, TYPE_MANUAL, "download", "failed", null, "未配置NAS地址")
            return@withContext false to "恢复失败：未配置NAS地址"
        }
        val zip = File(app.cacheDir, "restore.zip")
        try {
            val latest = NasClient.latestVersion(ip, port)
                ?: return@withContext false to "NAS 上暂无备份"
            NasClient.download(ip, port, latest.filename, zip)
            if (fileMd5(zip) != latest.md5) {
                zip.delete()
                recordLog(app, TYPE_MANUAL, "download", "failed", latest.filename, "MD5校验失败")
                return@withContext false to "恢复失败：下载文件校验不一致"
            }
            applyBackup(app, zip)
            recordLog(app, TYPE_MANUAL, "download", "success", latest.filename, null)
            true to "恢复成功，应用将重启"
        } catch (e: Exception) {
            zip.delete()
            recordLog(app, TYPE_MANUAL, "download", "failed", null, e.message)
            false to "恢复失败：${e.message ?: "网络错误"}"
        }
    }

    /** WAL checkpoint 后打包 数据库 + photos + qrs → cacheDir/bbg_backup_{version}.zip */
    private fun packBackup(context: Context): Pair<File, Long> {
        val db = AppDatabase.getInstance(context)
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }

        val version = System.currentTimeMillis()
        val zip = File(context.cacheDir, "bbg_backup_$version.zip")
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zip))).use { zos ->
            addZipEntry(zos, context.getDatabasePath(DB_NAME), DB_NAME)
            addZipDir(zos, context, DIR_PHOTOS)
            addZipDir(zos, context, DIR_QRS)
        }
        return zip to version
    }

    private fun addZipDir(zos: ZipOutputStream, context: Context, dir: String) {
        File(context.filesDir, dir).listFiles()?.forEach { file ->
            if (file.isFile) addZipEntry(zos, file, "$dir/${file.name}")
        }
    }

    private fun addZipEntry(zos: ZipOutputStream, file: File, entryName: String) {
        if (!file.exists()) return
        zos.putNextEntry(ZipEntry(entryName))
        file.inputStream().use { it.copyTo(zos, 64 * 1024) }
        zos.closeEntry()
    }

    /** 关库 → 覆盖数据库文件 → 解压照片/二维码（防 zip slip） */
    private fun applyBackup(context: Context, zip: File) {
        AppDatabase.closeAndReset()
        val dbFile = context.getDatabasePath(DB_NAME)
        dbFile.parentFile?.mkdirs()
        File(dbFile.parentFile, "$DB_NAME-wal").delete()
        File(dbFile.parentFile, "$DB_NAME-shm").delete()

        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                if (!name.contains("..")) {
                    val target: File? = when {
                        name == DB_NAME -> dbFile
                        name.startsWith("$DIR_PHOTOS/") -> File(context.filesDir, name)
                        name.startsWith("$DIR_QRS/") -> File(context.filesDir, name)
                        else -> null
                    }
                    target?.let { out ->
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { zis.copyTo(it, 64 * 1024) }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        zip.delete()
    }

    private suspend fun recordLog(
        context: Context,
        syncType: Int,
        direction: String,
        result: String,
        fileVersion: String?,
        message: String?
    ) {
        runCatching {
            AppDatabase.getInstance(context).syncLogDao().insert(
                SyncLogEntity(
                    syncType = syncType,
                    direction = direction,
                    result = result,
                    fileVersion = fileVersion,
                    message = message,
                    syncTime = System.currentTimeMillis()
                )
            )
        }
    }

    private fun fileMd5(file: File): String {
        val md = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}

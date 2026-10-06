package com.baibaoge.home.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.Constraints
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.baibaoge.home.sync.NetworkUtils
import com.baibaoge.home.sync.SyncConfigStore
import com.baibaoge.home.sync.SyncManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 自动同步 Worker（设计方案 3.5）：
 * WorkManager 每日凌晨 3 点周期执行；要求设备连接 WiFi 约束，
 * 运行时再次校验当前 SSID 必须命中家庭 WiFi 白名单，否则静默跳过（不写日志、不重试）。
 * 移动网络下由 WiFi 传输约束 + 运行时校验双重禁止。
 */
class AutoSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // 非 WiFi（含移动网络）一律不执行
        if (!NetworkUtils.isWifiConnected(applicationContext)) return Result.success()

        val ssid = NetworkUtils.currentSsid(applicationContext) ?: return Result.success()
        val whitelist = SyncConfigStore.ssids(applicationContext)
        if (whitelist.isEmpty() || ssid !in whitelist) return Result.success()

        // 命中白名单才真正执行上传；失败仅记日志，不影响本地使用
        SyncManager.syncNow(applicationContext, SyncManager.TYPE_AUTO)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "nas_auto_sync_daily"

        /** 调度每日凌晨 3 点的周期任务（KEEP：已存在则不重建） */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AutoSyncWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delayToNext3AM(), TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }

        /** 距下一个凌晨 3 点的毫秒数（今天已过 3 点则取明天） */
        private fun delayToNext3AM(): Long {
            val now = Calendar.getInstance()
            val next = (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 3)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (next.timeInMillis <= now.timeInMillis) {
                next.add(Calendar.DAY_OF_YEAR, 1)
            }
            return next.timeInMillis - now.timeInMillis
        }
    }
}

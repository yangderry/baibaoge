package com.baibaoge.home.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.baibaoge.home.data.AppDatabase
import com.baibaoge.home.util.NotificationHelper
import com.baibaoge.home.util.ReminderSettings
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 临期检查 Worker（设计方案 3.1.4）：
 * WorkManager 每日凌晨 2 点周期执行，遍历在库物品按分类阈值计算剩余天数，
 * 命中即通过系统通知栏推送（多条合并为汇总通知）。
 * 纯本地数据库计算，无需联网。
 */
class ExpiryCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            runCheck(applicationContext)
            Result.success()
        }.getOrElse {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "expiry_check_daily"

        /** 执行一次临期检查并推送通知；返回命中的物品数（供"立即检查"复用） */
        suspend fun runCheck(context: Context): Int {
            val dao = AppDatabase.getInstance(context).itemDao()
            val items = dao.getInStockWithExpiry()
            val expiring = items.mapNotNull { item ->
                val expiry = item.expiryDate ?: return@mapNotNull null
                val daysLeft = ReminderSettings.daysUntil(expiry)
                val threshold = ReminderSettings.thresholdFor(context, item.itemType)
                if (daysLeft <= threshold) item to daysLeft else null
            }.sortedBy { it.second }

            if (expiring.isNotEmpty()) {
                NotificationHelper.notifyExpiring(context, expiring)
            }
            return expiring.size
        }

        /** 调度每日凌晨 2 点的周期任务（KEEP：已存在则不重建） */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ExpiryCheckWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delayToNext2AM(), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }

        /** 距下一个凌晨 2 点的毫秒数（今天已过 2 点则取明天） */
        private fun delayToNext2AM(): Long {
            val now = Calendar.getInstance()
            val next = (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 2)
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

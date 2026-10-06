package com.baibaoge.home.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.baibaoge.home.MainActivity
import com.baibaoge.home.R
import com.baibaoge.home.data.entity.ItemEntity

/**
 * 临期提醒通知（设计方案 3.1.4）：
 * 每个临期物品一条子通知（点击深链到物品详情），多条时附加一条分组汇总通知。
 * 颜色语义：≤3 天红，3-7 天橙，>7 天黄（通知上体现为标题前缀色块文字）。
 */
object NotificationHelper {
    const val CHANNEL_ID = "expiry_reminder"
    private const val GROUP_KEY = "baibaoge_expiry_group"
    private const val SUMMARY_ID = 1000

    /** 应用启动时创建通知渠道（Android 8+） */
    fun ensureChannel(context: Context) {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID, "临期提醒", NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "物品临期/到期提醒"
            enableLights(true)
            lightColor = Color.RED
        }
        mgr.createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ActivityCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

    /** 物品详情的深链 PendingIntent：baibaoge://item/{itemId} */
    private fun detailPendingIntent(context: Context, itemId: String): PendingIntent {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("baibaoge://item/$itemId"),
            context, MainActivity::class.java
        )
        return PendingIntent.getActivity(
            context, itemId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 剩余天数文案 + 紧急度（0=红 ≤3，1=橙 3-7，2=黄 >7） */
    private fun urgency(daysLeft: Int): Pair<String, Int> = when {
        daysLeft < 0 -> "已过期 ${-daysLeft} 天" to 0
        daysLeft == 0 -> "今天到期" to 0
        daysLeft <= 3 -> "剩 $daysLeft 天" to 0
        daysLeft <= 7 -> "剩 $daysLeft 天" to 1
        else -> "剩 $daysLeft 天" to 2
    }

    /**
     * 推送临期提醒。items 已按剩余天数升序。
     * 单条：直接一条通知；多条：每项一条子通知 + 汇总通知（点击各子通知进详情）。
     */
    fun notifyExpiring(context: Context, items: List<Pair<ItemEntity, Int>>) {
        if (items.isEmpty() || !canNotify(context)) return
        ensureChannel(context)
        val nm = NotificationManagerCompat.from(context)

        items.forEachIndexed { index, (item, daysLeft) ->
            val (label, level) = urgency(daysLeft)
            val marker = when (level) { 0 -> "🔴"; 1 -> "🟠"; else -> "🟡" }
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("$marker ${item.name}")
                .setContentText("$label（${itemTypeLabel(item.itemType)}）")
                .setAutoCancel(true)
                .setContentIntent(detailPendingIntent(context, item.itemId))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            if (items.size > 1) builder.setGroup(GROUP_KEY)
            nm.notify(item.itemId.hashCode(), builder.build())
        }

        if (items.size > 1) {
            val red = items.count { it.second <= 3 }
            val style = NotificationCompat.InboxStyle()
            items.take(7).forEach { (item, daysLeft) ->
                style.addLine("${item.name} · ${urgency(daysLeft).first}")
            }
            if (items.size > 7) style.addLine("…等 ${items.size} 项")
            val summary = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("百宝格临期提醒")
                .setContentText("${items.size} 件物品临期，其中 $red 件 3 天内到期")
                .setStyle(style)
                .setGroup(GROUP_KEY)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setContentIntent(detailPendingIntent(context, items.first().first.itemId))
                .build()
            nm.notify(SUMMARY_ID, summary)
        }
    }

    private fun itemTypeLabel(type: Int): String = when (type) {
        ItemEntity.TYPE_FOOD -> "食品"
        ItemEntity.TYPE_MEDICINE -> "药品"
        ItemEntity.TYPE_CLOTHING -> "衣物"
        else -> "其他"
    }
}

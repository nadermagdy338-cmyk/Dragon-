/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 *
 * MaxManager proprietary source. See LICENSE at the repository root: this file is
 * MaxManager-owned and carries no third-party licence obligations.
 */

package nd.max.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import nd.max.MainActivity

/**
 * إشعار الخدمة الأمامية لكل تراكب — موضع واحد لثلاث نسخ كانت متطابقة.
 *
 * **ولماذا ليس تفصيلًا:** خدمة تراكب بلا إشعار أمامي تُقتل في الخلفية على أندرويد ٨ فما فوق،
 * والإشعار هو **العقد** الذي يُبقي النافذة حيّة ويُعلم المستخدم أنها تعمل ولها زرّ إيقاف.
 * ثلاثة تراكبات بأربع نسخ من هذا العقد تعني أن إصلاحًا في واحدة لا يصل أخواتها.
 *
 * والحدّ المعلن: `IMPORTANCE_MIN` مقصود — إشعار HUD يجب ألّا يُشغّل صوتًا ولا ينبض فوق لعبة
 * تُقاس، ومكان إخبار المستخدم هو النافذة وشاشة الإعدادات.
 *
 * @param serviceClass صنف الخدمة نفسها، فالإيقاف يستهدف مالك النافذة لا خدمة أخرى.
 */
internal data class OverlayNotice(
    val channelId: String,
    val notificationId: Int,
    val channelNameRes: Int,
    val titleRes: Int,
    val stopLabelRes: Int,
    val stopAction: String,
    val serviceClass: Class<out Service>
)

internal fun Service.startOverlayForeground(notice: OverlayNotice) {
    val manager = getSystemService(NotificationManager::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        manager.createNotificationChannel(
            NotificationChannel(
                notice.channelId,
                getString(notice.channelNameRes),
                NotificationManager.IMPORTANCE_MIN
            )
        )
    }

    val stop = PendingIntent.getService(
        this,
        0,
        Intent(this, notice.serviceClass).setAction(notice.stopAction),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    val open = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    val notification: Notification = NotificationCompat.Builder(this, notice.channelId)
        .setContentTitle(getString(notice.titleRes))
        .setSmallIcon(android.R.drawable.ic_menu_view)
        .setOngoing(true)
        .setSilent(true)
        .setShowWhen(false)
        .setContentIntent(open)
        .addAction(0, getString(notice.stopLabelRes), stop)
        .build()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        startForeground(
            notice.notificationId,
            notification,
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
    } else {
        startForeground(notice.notificationId, notification)
    }
}

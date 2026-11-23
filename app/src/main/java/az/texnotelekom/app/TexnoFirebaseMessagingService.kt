package az.texnotelekom.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class TexnoFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: "TEXNO-TELEKOM"
        val body = message.notification?.body ?: message.data["body"] ?: "Yeni proqram və ya oyun əlavə edildi."
        showNotification(title, body, message.data)
    }

    private fun showNotification(title: String, body: String, data: Map<String, String>) {
        val channelId = "texno_updates"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "TEXNO-TELEKOM bildirişləri", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Yeni proqramlar, oyunlar və tətbiq bildirişləri"
                }
            )
        }
        val programId = data["program_id"].orEmpty()
        val contentType = data["content_type"] ?: data["type"] ?: "program"
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (programId.isNotBlank()) {
                putExtra("notification_program_id", programId)
            }
            putExtra("notification_content_type", contentType)
            putExtra("open_admin_feedback", false)
        }
        val requestCode = if (programId.isNotBlank()) programId.hashCode() else System.currentTimeMillis().toInt()
        val pendingIntent = PendingIntent.getActivity(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        )
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.texno_notification_icon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()
        manager.notify(requestCode, notification)
    }
}

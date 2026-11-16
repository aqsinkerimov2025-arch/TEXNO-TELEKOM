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
        val body = message.notification?.body ?: message.data["body"] ?: "ADMİNLƏ-ƏLAQƏ bölməsinə yeni mesaj gəldi."
        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        val channelId = "admin_feedback_messages"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "ADMİNLƏ-ƏLAQƏ mesajları", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Yeni istifadəçi mesajları barədə bildirişlər"
                }
            )
        }
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_admin_feedback", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 4102, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        )
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.texno_logo)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()
        manager.notify(4102, notification)
    }
}
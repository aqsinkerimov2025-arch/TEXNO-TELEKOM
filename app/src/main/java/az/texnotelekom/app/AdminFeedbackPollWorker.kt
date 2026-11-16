package az.texnotelekom.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.Date

class AdminFeedbackPollWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val user = FirebaseAuth.getInstance().currentUser ?: return Result.success()
        if (user.uid != ADMIN_UID) return Result.success()
        return try {
            val prefs = applicationContext.getSharedPreferences("texno_admin_poll", Context.MODE_PRIVATE)
            val lastSeen = prefs.getLong("last_feedback_seen_at", System.currentTimeMillis())
            val snapshot = Tasks.await(FirebaseFirestore.getInstance().collection("admin_feedback")
                .whereGreaterThan("created_at", Date(lastSeen))
                .orderBy("created_at", Query.Direction.ASCENDING).limit(10).get())
            var newest = lastSeen
            snapshot.documents.forEach { doc ->
                val createdAt = doc.getTimestamp("created_at")?.toDate()?.time ?: return@forEach
                if (createdAt > newest) newest = createdAt
                val sender = (doc.getString("nickname") ?: "İstifadəçi").take(40)
                val body = if (!doc.getString("audio_url").isNullOrBlank()) "Sizə yeni səsli mesaj göndərildi."
                    else (doc.getString("text") ?: "Sizə yeni mesaj göndərildi.").take(120)
                showNotification(doc.id.hashCode(), sender, body)
            }
            if (newest > lastSeen) prefs.edit().putLong("last_feedback_seen_at", newest).apply()
            Result.success()
        } catch (_: Exception) { Result.retry() }
    }

    private fun showNotification(id: Int, sender: String, message: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "ADMİNLƏ-ƏLAQƏ mesajları", NotificationManager.IMPORTANCE_HIGH))
        if (Build.VERSION.SDK_INT >= 33 && applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("TEXNO-TELEKOM • Yeni mesaj")
            .setContentText("$sender: $message")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$sender: $message"))
            .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).build()
        NotificationManagerCompat.from(applicationContext).notify(id, notification)
    }

    companion object {
        private const val CHANNEL_ID = "admin_feedback_messages"
        private const val ADMIN_UID = "FflCErahavNIb8ZXsvHIyrxZYZH3"
    }
}
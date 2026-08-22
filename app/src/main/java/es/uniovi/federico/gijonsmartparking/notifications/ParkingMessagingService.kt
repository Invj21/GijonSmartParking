package es.uniovi.federico.gijonsmartparking.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import es.uniovi.federico.gijonsmartparking.MainActivity
import es.uniovi.federico.gijonsmartparking.R

/**
 * Riceve i push da Firebase Cloud Messaging (Task 6, la "feature cloud aggiuntiva"
 * indicata a teoria). L'app si iscrive al topic "parking_reminders" (vedi
 * ParkingApplication.onCreate); ogni messaggio mandato a quel topic dalla console
 * Firebase arriva qui e lo mostro come notifica locale, stesso canale del promemoria
 * WorkManager.
 */
class ParkingMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.notification?.title
            ?: message.data["title"]
            ?: getString(R.string.app_name)
        val body = message.notification?.body
            ?: message.data["body"]
            ?: return

        showNotification(title, body)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Il backend proprio (Task 1) non gestisce l'invio di push mirate: qui basta il
        // topic "parking_reminders" (broadcast a tutti gli iscritti dalla console Firebase).
        Log.d("FCM_TOKEN", "Nuovo token FCM: $token")
    }

    private fun showNotification(title: String, body: String) {
        NotificationHelper.ensureChannel(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_car)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(this).notify(FCM_NOTIFICATION_ID, notification)
    }

    companion object {
        const val TOPIC_PARKING_REMINDERS = "parking_reminders"
        private const val FCM_NOTIFICATION_ID = 43
    }
}

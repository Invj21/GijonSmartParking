package es.uniovi.federico.gijonsmartparking.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import es.uniovi.federico.gijonsmartparking.R

/**
 * Canale di notifica dell'app (obbligatorio da Android 8/API 26 in su): lo creo una volta
 * sola e lo riusano sia il promemoria locale (CarReminderWorker) sia i push in arrivo da
 * Firebase Cloud Messaging (ParkingMessagingService).
 */
object NotificationHelper {
    const val CHANNEL_ID = "car_reminders"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_description)
        }
        manager.createNotificationChannel(channel)
    }
}

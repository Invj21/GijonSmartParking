package es.uniovi.federico.gijonsmartparking.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import es.uniovi.federico.gijonsmartparking.MainActivity
import es.uniovi.federico.gijonsmartparking.R

/**
 * Job in background (WorkManager, per i task che devono girare anche se l'app è chiusa,
 * come visto a teoria) che mostra la notifica "non dimenticare l'auto" un po' di tempo
 * dopo aver salvato dove ho parcheggiato (Task 6, programmato da CarReminderScheduler).
 */
class CarReminderWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        val context = applicationContext
        NotificationHelper.ensureChannel(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        val parkedAtText = inputData.getString(KEY_PARKED_AT_TEXT) ?: ""

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_car)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(context.getString(R.string.reminder_body, parkedAtText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "car_reminder"
        const val KEY_PARKED_AT_TEXT = "parked_at_text"
        private const val NOTIFICATION_ID = 42
    }
}

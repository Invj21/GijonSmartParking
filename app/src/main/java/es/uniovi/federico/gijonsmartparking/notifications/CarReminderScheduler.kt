package es.uniovi.federico.gijonsmartparking.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Programma/cancella il promemoria "non dimenticare l'auto" (Task 6). Uso
 * enqueueUniqueWork con REPLACE così, se salvo una nuova posizione, il vecchio promemoria
 * (per il parcheggio precedente) viene sostituito invece di accumularsi.
 */
object CarReminderScheduler {

    // 2 ore dopo aver parcheggiato: abbastanza per non essere invadente ma utile a
    // ricordarsi dell'auto prima di dimenticarsene per il resto della giornata.
    private const val REMINDER_DELAY_MINUTES = 120L

    fun schedule(context: Context, parkedAtMillis: Long) {
        val parkedAtText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(parkedAtMillis)

        val request = OneTimeWorkRequestBuilder<CarReminderWorker>()
            .setInitialDelay(REMINDER_DELAY_MINUTES, TimeUnit.MINUTES)
            .setInputData(workDataOf(CarReminderWorker.KEY_PARKED_AT_TEXT to parkedAtText))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            CarReminderWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(CarReminderWorker.UNIQUE_WORK_NAME)
    }
}

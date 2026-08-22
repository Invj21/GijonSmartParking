package es.uniovi.federico.gijonsmartparking

import android.app.Application
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import es.uniovi.federico.gijonsmartparking.data.AppDatabase
import es.uniovi.federico.gijonsmartparking.data.AuthRepository
import es.uniovi.federico.gijonsmartparking.data.NetworkModule
import es.uniovi.federico.gijonsmartparking.data.ParkingRepository
import es.uniovi.federico.gijonsmartparking.data.TokenManager
import es.uniovi.federico.gijonsmartparking.notifications.ParkingMessagingService

/**
 * Classe Application: vive per tutta la durata dell'app. La uso per creare una volta
 * sola il database e il repository e tenerli in un posto unico, così tutti i Fragment
 * possono prenderli da qui (è la mia dependency injection "fatta a mano", senza librerie).
 *
 * È registrata nel Manifest con android:name=".ParkingApplication".
 */
class ParkingApplication : Application() {

    // by lazy = creo il database solo la prima volta che qualcuno lo usa davvero
    private val database by lazy { AppDatabase.getDatabase(this) }

    // Token JWT dell'utente loggato (login multi-utente, Task 2)
    val tokenManager by lazy { TokenManager(this) }

    // Un solo client per il MIO backend, condiviso tra AuthRepository e ParkingRepository
    // (preferiti/posizione auto, Task 3): così l'interceptor del token è creato una volta sola.
    private val backendApiService by lazy { NetworkModule.createBackendApiService(tokenManager) }

    val authRepository by lazy { AuthRepository(backendApiService, tokenManager) }

    // Un solo repository per tutta l'app; gli passo i due DAO e i due servizi di rete
    val repository by lazy {
        ParkingRepository(
            database.parkingDao(),
            database.carLocationDao(),
            NetworkModule.retrofitService,
            backendApiService,
            tokenManager
        )
    }

    override fun onCreate() {
        super.onCreate()

        // Feature cloud aggiuntiva (Task 6): mi iscrivo al topic dei promemoria parcheggio,
        // così ricevo tutti i messaggi che mando a quel topic dalla console Firebase.
        FirebaseMessaging.getInstance().subscribeToTopic(ParkingMessagingService.TOPIC_PARKING_REMINDERS)
            .addOnFailureListener { e ->
                Log.w("FCM_TOPIC", "Iscrizione al topic fallita: ${e.message}")
            }
    }
}

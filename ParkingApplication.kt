package es.uniovi.federico.gijonsmartparking

import android.app.Application
import es.uniovi.federico.gijonsmartparking.data.AppDatabase
import es.uniovi.federico.gijonsmartparking.data.NetworkModule
import es.uniovi.federico.gijonsmartparking.data.ParkingRepository

class ParkingApplication : Application() {
    // Il database può essere privato
    private val database by lazy { AppDatabase.getDatabase(this) }

    // IL REPOSITORY DEVE ESSERE PUBBLICO (senza la parola "private")
    val repository by lazy {
        ParkingRepository(database.parkingDao(), NetworkModule.retrofitService)
    }
}
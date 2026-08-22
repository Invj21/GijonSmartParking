package es.uniovi.federico.gijonsmartparking.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Il database vero e proprio (Room). Qui dichiaro quali tabelle contiene (entities)
 * e quali DAO espone. La versione la alzo ogni volta che cambio la struttura delle
 * tabelle (7 = aggiunto remotePhotoUrl a CarLocationEntity per la sync col backend, Task 3/5).
 */
@Database(entities = [ParkingEntity::class, CarLocationEntity::class], version = 7, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    // Room implementa da solo questi metodi e mi dà i DAO
    abstract fun parkingDao(): ParkingDao
    abstract fun carLocationDao(): CarLocationDao

    companion object {
        // Singleton: voglio una sola istanza del DB in tutta l'app.
        // @Volatile + synchronized così non si creano due DB se due thread entrano insieme.
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "parking_database"
                )
                    // se cambio versione, invece di scrivere una migrazione svuoto e ricreo:
                    // i dati sono solo una cache scaricata da internet, quindi posso perderli
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

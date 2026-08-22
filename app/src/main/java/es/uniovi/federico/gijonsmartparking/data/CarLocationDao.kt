package es.uniovi.federico.gijonsmartparking.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO della posizione dell'auto. Lavoro sempre sulla riga con id = 1 perché mi serve
 * salvarne una sola: l'ultima posizione dove ho lasciato la macchina.
 */
@Dao
interface CarLocationDao {

    // Flow così la schermata "trova la mia auto" si aggiorna da sola quando salvo/cancello
    @Query("SELECT * FROM car_location_table WHERE id = 1")
    fun getCarLocation(): Flow<CarLocationEntity?>

    // Lettura "una tantum" (non osservabile): mi serve nel repository per leggere la nota
    // locale prima di sovrascrivere la riga con i dati sincronizzati dal backend (Task 3).
    @Query("SELECT * FROM car_location_table WHERE id = 1")
    suspend fun getCarLocationOnce(): CarLocationEntity?

    // REPLACE: se c'era già una posizione la sovrascrivo
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCarLocation(location: CarLocationEntity)

    @Query("DELETE FROM car_location_table WHERE id = 1")
    suspend fun deleteCarLocation()
}

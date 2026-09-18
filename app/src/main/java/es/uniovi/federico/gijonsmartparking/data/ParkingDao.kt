package es.uniovi.federico.gijonsmartparking.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO = Data Access Object. È l'interfaccia con cui parlo alla tabella dei parcheggi:
 * scrivo le query in SQL e Room genera da solo il codice che le esegue.
 *
 * Come visto a teoria (Room, consultas asíncronas) Room non permette accessi nel thread
 * principale: per questo le letture "osservabili" tornano un Flow (si aggiornano da sole
 * quando la tabella cambia) e le scritture sono "suspend" (girano in una coroutine).
 */
@Dao
interface ParkingDao {

    // lettura osservabile: quando la tabella cambia, il Flow riemette la lista aggiornata
    @Query("SELECT * FROM parking_table ORDER BY name ASC")
    fun getAllParking(): Flow<List<ParkingEntity>>

    // ricerca per nome o città (la :query mi arriva già con le % dal repository)
    @Query("SELECT * FROM parking_table WHERE name LIKE :query OR city LIKE :query")
    fun searchParking(query: String): Flow<List<ParkingEntity>>

    // inserisco tutti i parcheggi scaricati; se un id esiste già lo sovrascrivo
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(parkingList: List<ParkingEntity>)

    // aggiorno solo il campo "preferito" di un parcheggio (lo chiama il cuore)
    @Query("UPDATE parking_table SET isFavorite = :isFav WHERE id = :parkingId")
    suspend fun updateFavorite(parkingId: Long, isFav: Boolean)

    // mi tengo gli id dei preferiti, così quando riscarico i dati non li perdo
    @Query("SELECT id FROM parking_table WHERE isFavorite = 1")
    suspend fun getFavoriteIds(): List<Long>

    // svuoto la tabella prima di reinserire i dati aggiornati
    @Query("DELETE FROM parking_table")
    suspend fun deleteAll()

    // --- Sincronizzazione preferiti col backend  ---

    @Query("UPDATE parking_table SET isFavorite = 0")
    suspend fun clearAllFavorites()

    @Query("UPDATE parking_table SET isFavorite = 1 WHERE id IN (:ids)")
    suspend fun markFavorites(ids: List<Long>)

    /**
     * Sostituisco i preferiti locali con quelli del backend.
     * @Transaction così l'interfaccia non vede mai uno stato intermedio "tutti
     * sfavoriti" tra le due query.
     */
    @Transaction
    suspend fun replaceFavorites(ids: List<Long>) {
        clearAllFavorites()
        markFavorites(ids)
    }
}

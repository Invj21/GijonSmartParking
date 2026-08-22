package es.uniovi.federico.gijonsmartparking.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tabella dove salvo dove ho parcheggiato l'auto ("trova la mia auto").
 * Mi serve una sola riga alla volta, quindi uso sempre id = 1 come chiave fissa:
 * così ogni salvataggio sovrascrive il precedente invece di accumulare righe.
 */
@Entity(tableName = "car_location_table")
data class CarLocationEntity(
    @PrimaryKey val id: Int = 1,
    val latitude: Double,
    val longitude: Double,
    val note: String,               // nota dell'utente (es. "Piano 2")
    val timestamp: Long,            // quando ho salvato, per mostrare data/ora
    val imagePath: String? = null,  // percorso LOCALE della foto scattata su questo dispositivo
    val remotePhotoUrl: String? = null // URL della foto sincronizzata dal backend (Task 3/5:
                                        // valorizzato quando la posizione arriva da un altro
                                        // dispositivo o da una reinstallazione, non da imagePath)
)

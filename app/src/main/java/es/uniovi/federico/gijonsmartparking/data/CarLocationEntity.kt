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
    val note: String,
    val timestamp: Long,
    val imagePath: String? = null,
    val remotePhotoUrl: String? = null

)

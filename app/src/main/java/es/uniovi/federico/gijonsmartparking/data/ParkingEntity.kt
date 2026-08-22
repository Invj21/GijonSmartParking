package es.uniovi.federico.gijonsmartparking.data

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

/**
 * Questa è la tabella dei parcheggi nel database (Room). Ogni oggetto = una riga.
 * La annoto con @Entity così Room mi crea la tabella "parking_table".
 *
 * La faccio anche Parcelable (@Parcelize) perché con il Navigation Component devo
 * passare il parcheggio intero dalla lista al dettaglio come argomento, e per farlo
 * l'oggetto deve essere "impacchettabile".
 */
@Parcelize
@Entity(tableName = "parking_table")
data class ParkingEntity(
    @PrimaryKey val id: Long,   // id che arriva da OpenStreetMap, è già univoco
    val name: String,
    val city: String,
    val lat: Double,
    val lon: Double,
    val type: String,           // superficie / sotterraneo / multipiano...
    val fee: String,            // yes / no / unknown
    val capacity: Int?,         // posti totali (può mancare, quindi nullable)
    val wheelchair: String,     // accessibilità: yes / no / limited / unknown
    val covered: String = "unknown",      // coperto: yes / no / unknown
    val openingHours: String = "unknown", // orari OSM, es. "24/7" o "Mo-Fr 08:00-20:00"
    val charge: String = "",              // tariffa scritta (tag "charge"), se c'è
    val hasCharging: Boolean = false,     // true se ha una colonnina di ricarica elettrica
    val vehicle: String = "car",          // tipo di veicolo: "car" (auto) o "motorcycle" (moto/scooter)
    val hasPinkParking: Boolean = false,  // true se ha posti "rosa" (donne in gravidanza / genitori)
    val isFavorite: Boolean = false       // lo segna l'utente col cuore
) : Parcelable

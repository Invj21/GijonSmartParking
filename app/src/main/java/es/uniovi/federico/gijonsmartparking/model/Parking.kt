package es.uniovi.federico.gijonsmartparking.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Modello "di dominio" del parcheggio, cioè come lo penso io a livello concettuale.
 *
 * NOTA: alla fine nell'app uso direttamente [es.uniovi.federico.gijonsmartparking.data.ParkingEntity]
 * (l'entità di Room) sia per il database sia per passarla tra i Fragment, perché è già
 * Parcelable e mi evita di duplicare/convertire i dati. Questa classe è stata creata
 * all'inizio per separare "modello" e "dato del DB"
 * e la tengo come riferimento, ma al momento non è collegata al resto.
 */
@Parcelize
data class Parking(
    val id: Long,
    val name: String?,       // nome del parcheggio
    val city: String?,       // città / comune
    val lat: Double,
    val lon: Double,
    val type: String?,       // surface, multi-storey, underground...
    val fee: String?,        // yes / no
    val capacity: Int?,      // numero di posti
    val wheelchair: String?, // yes / no / limited
    val covered: String? = null,       // coperto: yes / no
    val openingHours: String? = null,  // orari (tag opening_hours)
    val charge: String? = null,        // tariffa testuale
    val hasCharging: Boolean = false,  // ricarica elettrica disponibile
    val vehicle: String? = null,       // "car" o "motorcycle"
    val hasPinkParking: Boolean = false, // posti rosa disponibili
    var isFavorite: Boolean = false
) : Parcelable

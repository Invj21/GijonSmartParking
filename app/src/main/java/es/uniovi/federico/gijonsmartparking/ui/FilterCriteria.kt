package es.uniovi.federico.gijonsmartparking.ui

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

// Uso degli enum invece di stringhe sparse così i valori possibili sono chiusi e non sbaglio
/** Tariffa scelta nei filtri: tutti / solo gratis / solo a pagamento. */
enum class FeeFilter { ALL, FREE, PAID }

/** Copertura: tutti / solo coperti / solo scoperti. */
enum class CoverFilter { ALL, COVERED, UNCOVERED }

/** Orari: tutti / aperti h24 / aperti adesso. */
enum class HoursFilter { ALL, OPEN_24H, OPEN_NOW }

/** Tipo di veicolo: tutti / solo auto / solo moto-scooter. */
enum class VehicleFilter { ALL, CAR, MOTORCYCLE }

/**
 * Raccoglie in un solo oggetto tutti i filtri scelti dall'utente. Seguo l'idea dell'UDF
 * vista a teoria: questo è uno "stato immutabile" (data class) che il ViewModel tiene e
 * la UI legge. Quando l'utente cambia qualcosa creo un NUOVO oggetto invece di modificarlo.
 * È Parcelable perché lo passo al bottom sheet dei filtri.
 */
@Parcelize
data class FilterCriteria(
    val fee: FeeFilter = FeeFilter.ALL,
    val cover: CoverFilter = CoverFilter.ALL,
    val hours: HoursFilter = HoursFilter.ALL,
    val vehicle: VehicleFilter = VehicleFilter.ALL,
    val onlyCharging: Boolean = false,
    val onlyAccessible: Boolean = false,
    val onlyPink: Boolean = false,
    val nearbyEnabled: Boolean = false,
    val radiusKm: Float = 5f
) : Parcelable {

    /** Vero se c'è almeno un filtro attivo (così posso evidenziare il pulsante filtri). */
    val isActive: Boolean
        get() = fee != FeeFilter.ALL ||
                cover != CoverFilter.ALL ||
                hours != HoursFilter.ALL ||
                vehicle != VehicleFilter.ALL ||
                onlyCharging ||
                onlyAccessible ||
                onlyPink ||
                nearbyEnabled
}

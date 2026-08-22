package es.uniovi.federico.gijonsmartparking.utils

import android.content.Context
import es.uniovi.federico.gijonsmartparking.R

/**
 * Anche questa la tengo in "utils". Serve a tradurre i valori che arrivano da
 * OpenStreetMap, che sono sempre in inglese ("surface", "underground", "yes"...).
 * Senza questa classe, se l'utente mette l'app in italiano vedrebbe comunque le
 * parole inglesi nelle card: qui invece mappo ogni valore alla stringa giusta in
 * res/values (inglese) o res/values-it (italiano), così cambia da sola con la lingua.
 */
object ParkingLabels {

    // tipo di parcheggio (superficie, sotterraneo, ecc.)
    fun type(context: Context, raw: String?): String = when (raw?.lowercase()) {
        "surface" -> context.getString(R.string.type_surface)
        "underground" -> context.getString(R.string.type_underground)
        "multi-storey", "multi_storey", "multistorey" -> context.getString(R.string.type_multistorey)
        "rooftop" -> context.getString(R.string.type_rooftop)
        "street_side" -> context.getString(R.string.type_street_side)
        "lane" -> context.getString(R.string.type_lane)
        "garage_boxes", "garage", "garages" -> context.getString(R.string.type_garage_boxes)
        "carports", "carport" -> context.getString(R.string.type_carports)
        null, "", "unknown" -> context.getString(R.string.not_available)
        else -> raw // se è un valore che non avevo previsto lascio l'originale
    }

    // accessibilità per sedia a rotelle
    fun wheelchair(context: Context, raw: String?): String = when (raw?.lowercase()) {
        "yes" -> context.getString(R.string.value_yes)
        "no" -> context.getString(R.string.value_no)
        "limited" -> context.getString(R.string.wheelchair_limited)
        "designated" -> context.getString(R.string.wheelchair_designated)
        else -> context.getString(R.string.not_available)
    }

    // a pagamento / gratis
    fun fee(context: Context, raw: String?): String =
        if (raw == "yes") context.getString(R.string.fee_paid)
        else context.getString(R.string.fee_free)

    // coperto / scoperto
    fun covered(context: Context, raw: String?): String = when (raw) {
        "yes" -> context.getString(R.string.value_yes)
        "no" -> context.getString(R.string.value_no)
        else -> context.getString(R.string.not_available)
    }
}

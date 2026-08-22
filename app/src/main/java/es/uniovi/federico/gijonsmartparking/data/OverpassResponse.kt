package es.uniovi.federico.gijonsmartparking.data

/**
 * Queste classi rappresentano il JSON che mi torna l'API Overpass di OpenStreetMap.
 * Moshi (il convertitore che ho messo in Retrofit) legge il JSON e riempie questi oggetti:
 * i nomi dei campi devono combaciare con quelli del JSON ("elements", "tags"...).
 */
data class OverpassResponse(
    val elements: List<OverpassElement>
)

/**
 * Un singolo elemento OSM (un parcheggio oppure una colonnina di ricarica).
 * I "node" (punti) hanno lat/lon diretti; le "way" (aree) hanno il centro in "center".
 * In "tags" c'è la mappa con tutte le info: name, fee, capacity, opening_hours, ecc.
 * Sono nullable perché non tutti gli elementi hanno tutti i campi.
 */
data class OverpassElement(
    val id: Long,
    val lat: Double?,
    val lon: Double?,
    val center: OverpassCenter?,
    val tags: Map<String, String>?
)

// centro geometrico, usato dalle "way" che non hanno un lat/lon singolo
data class OverpassCenter(
    val lat: Double,
    val lon: Double
)

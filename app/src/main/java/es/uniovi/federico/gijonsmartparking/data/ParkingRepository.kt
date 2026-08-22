package es.uniovi.federico.gijonsmartparking.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.File
import java.time.OffsetDateTime

/**
 * Il Repository è il pezzo centrale del "layer dei dati" di cui parla la teoria (MVVM):
 * è l'unica "fonte di verità" per il resto dell'app. Il ViewModel non sa né di Room né
 * di Retrofit, parla solo con questa classe; qui dentro decido io se i dati vengono dal
 * database locale o da internet.
 *
 * Riceve nel costruttore i due DAO (database) e i servizi di rete: glieli passa
 * la ParkingApplication (una specie di dependency injection fatta a mano).
 *
 * backendApiService/tokenManager servono per la sincronizzazione preferiti/posizione auto
 * col MIO backend (Task 3): Room resta SEMPRE la fonte locale (offline-first, come visto
 * a teoria), il backend è "best effort" quando l'utente è loggato e c'è connessione.
 */
class ParkingRepository(private val parkingDao: ParkingDao,
                        private val carLocationDao: CarLocationDao,
                        private val apiService: ParkingApiService,
                        private val backendApiService: BackendApiService,
                        private val tokenManager: TokenManager) {

    // Lista dei parcheggi sempre aggiornata dal DB: la passo così com'è al ViewModel
    val allParking: Flow<List<ParkingEntity>> = parkingDao.getAllParking()

    /**
     * Scarica i parcheggi da OpenStreetMap e li salva nel database.
     * withContext(IO) = sposto il lavoro pesante (rete + DB) su un thread secondario,
     * così non blocco l'interfaccia (regola vista a teoria sui thread).
     */
    suspend fun refreshParking() {
        withContext(Dispatchers.IO) {
            try {
                Log.d("PARKING_TEST", "Richiesta API in corso...")

                // Bounding box che copre TUTTE le Asturie -> (sud, ovest, nord, est)
                val bbox = "$ASTURIAS_SOUTH,$ASTURIAS_WEST,$ASTURIAS_NORTH,$ASTURIAS_EAST"

                // In un'unica chiamata chiedo:
                //  - i parcheggi per auto (amenity=parking)
                //  - i parcheggi per moto/scooter (amenity=motorcycle_parking)
                //  - le colonnine di ricarica, per capire quali parcheggi hanno la ricarica vicino
                val query = "[out:json][timeout:90];(" +
                        "node[\"amenity\"=\"parking\"]($bbox);" +
                        "way[\"amenity\"=\"parking\"]($bbox);" +
                        "node[\"amenity\"=\"motorcycle_parking\"]($bbox);" +
                        "way[\"amenity\"=\"motorcycle_parking\"]($bbox);" +
                        "node[\"amenity\"=\"charging_station\"]($bbox);" +
                        ");out center;"

                Log.d("PARKING_TEST", "Richiesta inviata, attendo risposta dal server...")
                val response = apiService.getAsturiasParking(query)
                Log.d("PARKING_TEST", "Risposta ricevuta. Elementi trovati: ${response.elements.size}")

                // Tengo auto e moto. Regola sul nome: per le AUTO pretendo un nome vero (così
                // la lista resta pulita, come deciso prima); per le MOTO accetto anche senza nome
                // perché su OSM quasi nessuna ce l'ha, ma le voglio comunque mostrare (gli darò
                // un nome generico dopo). In entrambi i casi servono coordinate valide.
                val parkingElements = response.elements.filter { el ->
                    val amenity = el.tags?.get("amenity")
                    val hasCoords = (el.lat ?: el.center?.lat) != null && (el.lon ?: el.center?.lon) != null
                    hasCoords && when (amenity) {
                        // qui amenity != null implica tags != null (Kotlin lo capisce da solo)
                        "parking" -> !el.tags["name"].isNullOrBlank()
                        "motorcycle_parking" -> true
                        else -> false
                    }
                }
                // Prendo le coordinate di tutte le colonnine, mi serviranno per la distanza
                val chargingPoints = response.elements
                    .filter { it.tags?.get("amenity") == "charging_station" }
                    .mapNotNull { cs ->
                        val lat = cs.lat ?: cs.center?.lat
                        val lon = cs.lon ?: cs.center?.lon
                        if (lat != null && lon != null) lat to lon else null
                    }
                Log.d("PARKING_TEST", "Parcheggi: ${parkingElements.size}, Colonnine: ${chargingPoints.size}")

                if (parkingElements.isNotEmpty()) {
                    // Mi salvo quali parcheggi erano preferiti PRIMA di svuotare la tabella,
                    // così dopo posso rimetterli (altrimenti il refresh azzererebbe i cuori).
                    val favoriteIds = parkingDao.getFavoriteIds().toSet()

                    // Trasformo ogni elemento OSM in una riga del mio database (ParkingEntity)
                    val entities = parkingElements.map { element ->
                        val tags = element.tags
                        val lat = element.lat ?: element.center?.lat ?: 0.0
                        val lon = element.lon ?: element.center?.lon ?: 0.0
                        val type = tags?.get("parking") ?: "surface"
                        // se l'amenity è motorcycle_parking è un parcheggio per moto/scooter
                        val vehicle = if (tags?.get("amenity") == "motorcycle_parking") "motorcycle" else "car"

                        ParkingEntity(
                            id = element.id,
                            // le moto spesso non hanno nome: lascio "" e ci penserà la UI a
                            // metterci un nome generico tradotto ("Parcheggio moto")
                            name = tags?.get("name") ?: "",
                            city = tags?.get("addr:city") ?: "Asturias",
                            lat = lat,
                            lon = lon,
                            type = type,
                            fee = tags?.get("fee") ?: "unknown",
                            capacity = tags?.get("capacity")?.toIntOrNull(),
                            wheelchair = tags?.get("wheelchair") ?: "unknown",
                            covered = resolveCovered(tags, type),
                            openingHours = tags?.get("opening_hours") ?: "unknown",
                            charge = tags?.get("charge") ?: tags?.get("fee:conditional") ?: "",
                            hasCharging = resolveHasCharging(tags, lat, lon, chargingPoints),
                            vehicle = vehicle,
                            hasPinkParking = resolvePinkParking(tags),
                            isFavorite = favoriteIds.contains(element.id) // mantengo il preferito
                        )
                    }

                    // svuoto e reinserisco: così il DB ha sempre i dati freschi di tutta l'Asturia
                    parkingDao.deleteAll()
                    parkingDao.insertAll(entities)
                    Log.d("PARKING_TEST", "Salvataggio nel database completato: ${entities.size} record.")

                    // Se sono loggato, i preferiti del backend sono autoritativi (stessi su
                    // tutti i dispositivi); se la chiamata fallisce tengo quelli locali appena
                    // ripristinati sopra (offline-first).
                    syncFavoritesFromBackend()
                } else {
                    Log.w("PARKING_TEST", "L'API ha restituito 0 elementi. Verifica la query o la connessione.")
                }

            } catch (e: Exception) {
                // se la rete va male non voglio che l'app crashi: loggo e basta,
                // tanto in lista restano i dati scaricati l'ultima volta
                Log.e("PARKING_TEST", "ERRORE NEL REPOSITORY: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    /** Capisco se il parcheggio è coperto: prima guardo il tag, se manca lo deduco dal tipo. */
    private fun resolveCovered(tags: Map<String, String>?, type: String): String {
        tags?.get("covered")?.let { return it } // se OSM lo dice, mi fido ("yes"/"no")
        return when (type) {
            "underground", "multi-storey", "multi_storey", "garage_boxes" -> "yes" // questi sono al chiuso
            "surface", "lane", "street_side" -> "no"                                 // questi sono all'aperto
            else -> "unknown"
        }
    }

    /**
     * Capisco se ci sono "posti rosa" (riservati a donne in gravidanza / genitori con bimbi).
     * Su OSM non esiste un tag unico per questo, quindi controllo quelli più usati:
     * capacity:parent (posti famiglia), capacity:women, parking_space=parent, o chiavi che
     * contengono "parent"/"women"/"pregnan". Nota: pochi parcheggi sono taggati così.
     */
    private fun resolvePinkParking(tags: Map<String, String>?): Boolean {
        if (tags == null) return false
        if (tags.containsKey("capacity:parent")) return true
        if (tags.containsKey("capacity:women")) return true
        if (tags["parking_space"] == "parent") return true
        return tags.keys.any { k ->
            k.contains("parent") || k.contains("women") || k.contains("pregnan")
        }
    }

    /**
     * Un parcheggio ha la ricarica elettrica se:
     *  - lo dicono i suoi tag (capacity:charging, socket:*, charging_station=yes), oppure
     *  - c'è una colonnina molto vicina (entro 120 m): in quel caso lo considero "con ricarica".
     */
    private fun resolveHasCharging(
        tags: Map<String, String>?,
        lat: Double,
        lon: Double,
        chargingPoints: List<Pair<Double, Double>>
    ): Boolean {
        if (tags != null) {
            if (tags.containsKey("capacity:charging")) return true
            if (tags["charging_station"] == "yes") return true
            if (tags.keys.any { it.startsWith("socket:") }) return true
        }
        // controllo se almeno una colonnina è abbastanza vicina
        return chargingPoints.any { (cLat, cLon) ->
            distanceMeters(lat, lon, cLat, cLon) <= 120.0
        }
    }

    /** Distanza in metri tra due punti GPS (formula dell'emisenoverso / haversine). */
    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0 // raggio medio della Terra in metri
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

    companion object {
        // angoli del rettangolo che racchiude le Asturie (valori approssimati presi dalla mappa)
        private const val ASTURIAS_SOUTH = 42.85
        private const val ASTURIAS_WEST = -7.25
        private const val ASTURIAS_NORTH = 43.70
        private const val ASTURIAS_EAST = -4.45
    }

    // ricerca: aggiungo le % qui così il DAO fa la LIKE "contiene"
    fun searchParking(query: String): Flow<List<ParkingEntity>> {
        return parkingDao.searchParking("%$query%")
    }

    /** Salva/toglie un parcheggio dai preferiti (lo chiama il ViewModel). */
    suspend fun setFavorite(parkingId: Long, isFavorite: Boolean) {
        withContext(Dispatchers.IO) {
            // Room prima di tutto: l'interfaccia deve rispondere subito, anche offline
            parkingDao.updateFavorite(parkingId, isFavorite)

            if (tokenManager.hasToken()) {
                try {
                    if (isFavorite) {
                        backendApiService.addFavorite(AddFavoriteRequest(parkingId))
                    } else {
                        backendApiService.removeFavorite(parkingId)
                    }
                } catch (e: Exception) {
                    // niente rete o backend giù: il preferito resta comunque salvato in locale,
                    // verrà riallineato al prossimo refresh riuscito
                    Log.w("PARKING_SYNC", "Impossibile sincronizzare il preferito: ${e.message}")
                }
            }
        }
    }

    /** Scarico i preferiti dal backend e li applico a Room (autoritativo per l'utente loggato). */
    private suspend fun syncFavoritesFromBackend() {
        if (!tokenManager.hasToken()) return
        try {
            val remoteIds = backendApiService.getFavorites().map { it.parkingId }
            parkingDao.replaceFavorites(remoteIds)
        } catch (e: Exception) {
            Log.w("PARKING_SYNC", "Impossibile scaricare i preferiti dal server: ${e.message}")
        }
    }

    // --- Parte "trova la mia auto": stesso schema, ma sulla tabella della posizione ---

    val carLocation: Flow<CarLocationEntity?> = carLocationDao.getCarLocation()

    suspend fun saveCarLocation(lat: Double, lon: Double, note: String, imagePath: String?) {
        val location = CarLocationEntity(
            latitude = lat,
            longitude = lon,
            note = note,
            timestamp = System.currentTimeMillis(), // ora del salvataggio
            imagePath = imagePath
        )
        // Room prima di tutto: funziona sempre, anche senza account/rete
        carLocationDao.saveCarLocation(location)

        if (tokenManager.hasToken()) {
            withContext(Dispatchers.IO) {
                try {
                    val photoFile = imagePath?.let { File(it) }?.takeIf { it.exists() }
                    if (photoFile != null) {
                        val latPart = lat.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                        val lngPart = lon.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                        val photoPart = MultipartBody.Part.createFormData(
                            "photo", photoFile.name, photoFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
                        )
                        backendApiService.saveCarLocationWithPhoto(latPart, lngPart, photoPart)
                    } else {
                        backendApiService.saveCarLocation(SaveCarLocationRequest(lat, lon))
                    }
                } catch (e: Exception) {
                    Log.w("PARKING_SYNC", "Impossibile sincronizzare la posizione dell'auto: ${e.message}")
                }
            }
        }
    }

    /**
     * Al caricamento della schermata "trova la mia auto" provo prima il backend (dati
     * aggiornati da eventuali altri dispositivi); se fallisce (offline) resta quello che
     * c'è già in Room, che rimane comunque la fonte mostrata dalla UI (vedi [carLocation]).
     */
    suspend fun syncCarLocationFromBackend() {
        if (!tokenManager.hasToken()) return
        withContext(Dispatchers.IO) {
            try {
                val remote = backendApiService.getCarLocation()
                // la nota non esiste sul backend (solo lat/lng/foto): tengo quella locale
                val local = carLocationDao.getCarLocationOnce()
                val timestamp = runCatching {
                    OffsetDateTime.parse(remote.savedAt).toInstant().toEpochMilli()
                }.getOrDefault(System.currentTimeMillis())

                // Se la posizione locale è la STESSA di quella appena scaricata (stesso
                // dispositivo che l'ha salvata) tengo il file della foto locale invece di
                // ributtarmi sulla rete per un'immagine che ho già sul disco.
                val sameLocation = local != null &&
                        kotlin.math.abs(local.latitude - remote.lat) < 0.0001 &&
                        kotlin.math.abs(local.longitude - remote.lng) < 0.0001
                val localPhotoStillValid = sameLocation && local?.imagePath?.let { File(it).exists() } == true

                carLocationDao.saveCarLocation(
                    CarLocationEntity(
                        latitude = remote.lat,
                        longitude = remote.lng,
                        note = local?.note ?: "",
                        timestamp = timestamp,
                        imagePath = if (localPhotoStillValid) local?.imagePath else null,
                        remotePhotoUrl = if (localPhotoStillValid) null else
                            remote.photoUrl?.let { NetworkModule.resolveBackendUrl(it) }
                    )
                )
            } catch (e: HttpException) {
                // 404 = nessuna posizione salvata sul server: non è un errore, tengo quella locale
                if (e.code() != 404) {
                    Log.w("PARKING_SYNC", "Errore nel leggere la posizione dell'auto dal server: ${e.message}")
                } else {
                    Unit
                }
            } catch (e: Exception) {
                Log.w("PARKING_SYNC", "Impossibile contattare il server per la posizione dell'auto: ${e.message}")
            }
        }
    }

    suspend fun clearCarLocation() {
        carLocationDao.deleteCarLocation()

        if (tokenManager.hasToken()) {
            withContext(Dispatchers.IO) {
                try {
                    backendApiService.deleteCarLocation()
                } catch (e: Exception) {
                    Log.w("PARKING_SYNC", "Impossibile cancellare la posizione dell'auto sul server: ${e.message}")
                }
            }
        }
    }
}

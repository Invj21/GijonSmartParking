package es.uniovi.federico.gijonsmartparking.ui

import androidx.lifecycle.*
import es.uniovi.federico.gijonsmartparking.data.ParkingRepository
import es.uniovi.federico.gijonsmartparking.data.ParkingEntity
import es.uniovi.federico.gijonsmartparking.utils.OpeningHoursUtil
import kotlinx.coroutines.launch
import es.uniovi.federico.gijonsmartparking.data.CarLocationEntity

/**
 * ViewModel della parte parcheggi (è il cuore del pattern MVVM visto a teoria).
 * Sta in mezzo tra la UI e il repository: espone i dati alla vista tramite LiveData e
 * NON tiene riferimenti ai Fragment, così sopravvive ai cambi di configurazione
 * (es. rotazione) senza perdere lo stato.
 *
 * I Fragment leggono "parkingList" e gli mandano gli eventi (ricerca, filtri, preferiti):
 * questo è il flusso di dati unidirezionale (UDF) delle slide.
 */
class ParkingViewModel(private val repository: ParkingRepository) : ViewModel() {

    // testo cercato dall'utente; lo tengo privato e mutabile, fuori espongo solo letture
    private val _searchQuery = MutableLiveData<String>("")

    // filtri attivi (parto da quelli di default = nessun filtro)
    private val _filter = MutableLiveData(FilterCriteria())
    val filter: LiveData<FilterCriteria> get() = _filter

    // ultima posizione GPS dell'utente (lat, lon), serve al filtro "vicino a me"
    private val _userLocation = MutableLiveData<Pair<Double, Double>?>(null)
    val userLocation: LiveData<Pair<Double, Double>?> get() = _userLocation

    // lista "grezza": dipende solo dalla ricerca. switchMap = se cambia la ricerca,
    // cambio anche la sorgente dati (tutti i parcheggi oppure solo quelli cercati)
    private val baseList: LiveData<List<ParkingEntity>> = _searchQuery.switchMap { query ->
        if (query.isNullOrEmpty()) {
            repository.allParking.asLiveData()
        } else {
            repository.searchParking(query).asLiveData()
        }
    }

    // lista finale che vede la UI: un MediatorLiveData che "fonde" tre sorgenti
    // (lista grezza + filtri + posizione). Se cambia una qualsiasi, rifaccio i filtri.
    val parkingList = MediatorLiveData<List<ParkingEntity>>().apply {
        addSource(baseList) { value = applyFilters() }
        addSource(_filter) { value = applyFilters() }
        addSource(_userLocation) { value = applyFilters() }
    }

    // applico tutti i filtri attivi alla lista grezza
    private fun applyFilters(): List<ParkingEntity> {
        val list = baseList.value ?: emptyList()
        val criteria = _filter.value ?: FilterCriteria()
        val location = _userLocation.value

        return list.filter { p ->
            // tariffa
            when (criteria.fee) {
                FeeFilter.ALL -> true
                FeeFilter.FREE -> p.fee == "no"
                FeeFilter.PAID -> p.fee == "yes"
            } &&
            // copertura
            when (criteria.cover) {
                CoverFilter.ALL -> true
                CoverFilter.COVERED -> p.covered == "yes"
                CoverFilter.UNCOVERED -> p.covered == "no"
            } &&
            // orari (delego il calcolo difficile a OpeningHoursUtil)
            when (criteria.hours) {
                HoursFilter.ALL -> true
                HoursFilter.OPEN_24H -> OpeningHoursUtil.is24x7(p.openingHours)
                HoursFilter.OPEN_NOW -> OpeningHoursUtil.isOpenNow(p.openingHours)
            } &&
            // tipo di veicolo (auto / moto)
            when (criteria.vehicle) {
                VehicleFilter.ALL -> true
                VehicleFilter.CAR -> p.vehicle == "car"
                VehicleFilter.MOTORCYCLE -> p.vehicle == "motorcycle"
            } &&
            // solo con colonnina elettrica
            (!criteria.onlyCharging || p.hasCharging) &&
            // solo accessibili (sedia a rotelle "yes" o "limited")
            (!criteria.onlyAccessible || p.wheelchair == "yes" || p.wheelchair == "limited") &&
            // solo con posti rosa
            (!criteria.onlyPink || p.hasPinkParking) &&
            // dentro il raggio scelto (se non ho la posizione lascio passare tutti)
            (!criteria.nearbyEnabled || location == null ||
                    distanceKm(location.first, location.second, p.lat, p.lon) <= criteria.radiusKm)
        }.let { filtered ->
            // con "vicino a me" attivo ordino dal più vicino al più lontano
            if (criteria.nearbyEnabled && location != null) {
                filtered.sortedBy { distanceKm(location.first, location.second, it.lat, it.lon) }
            } else {
                filtered
            }
        }
    }

    /** Distanza in km tra due punti GPS (haversine), per il filtro del raggio. */
    private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // raggio Terra in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

    // --- Eventi che arrivano dai Fragment: aggiornano lo stato e fanno ricalcolare la lista ---

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(criteria: FilterCriteria) {
        _filter.value = criteria
    }

    fun setUserLocation(lat: Double, lon: Double) {
        _userLocation.value = lat to lon
    }

    /** Salva/toglie un preferito; viewModelScope.launch perché è un'operazione sospesa sul DB. */
    fun setFavorite(parkingId: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            repository.setFavorite(parkingId, isFavorite)
        }
    }

    // mostro/nascondo la rotellina di caricamento durante il download
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> get() = _isLoading

    fun refreshData() {
        // viewModelScope: la coroutine si cancella da sola se il ViewModel viene distrutto
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.refreshParking()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    // --- Parte "trova la mia auto" ---

    val carLocation: LiveData<CarLocationEntity?> = repository.carLocation.asLiveData()

    /** Chiamato all'apertura di "trova la mia auto": prova a riallineare col backend (Task 3). */
    fun syncCarLocation() {
        viewModelScope.launch {
            repository.syncCarLocationFromBackend()
        }
    }

    fun saveCarLocation(lat: Double, lon: Double, note: String, imagePath: String?) {
        viewModelScope.launch {
            repository.saveCarLocation(lat, lon, note, imagePath)
        }
    }

    fun deleteCarLocation() {
        viewModelScope.launch {
            repository.clearCarLocation()
        }
    }

    /** Pulisce preferiti/posizione auto in locale, dopo l'eliminazione dell'account. */
    fun clearLocalUserData() {
        viewModelScope.launch {
            repository.clearLocalUserData()
        }
    }
}

/**
 * Factory: il mio ViewModel ha un parametro nel costruttore (il repository), perciò
 * Android non sa crearlo da solo. Questa classe gli spiega come fare.
 */
class ParkingViewModelFactory(private val repository: ParkingRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ParkingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ParkingViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

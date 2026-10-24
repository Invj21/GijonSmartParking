package es.uniovi.federico.gijonsmartparking.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.tasks.CancellationTokenSource
import androidx.navigation.fragment.findNavController
import es.uniovi.federico.gijonsmartparking.ParkingApplication
import es.uniovi.federico.gijonsmartparking.R
import es.uniovi.federico.gijonsmartparking.data.ParkingEntity

/**
 * Mostra i parcheggi sulla mappa di Google (Google Play Services). Implemento
 * OnMapReadyCallback perché la mappa si carica in modo asincrono: il codice nei marker
 * va messo in onMapReady, cioè quando la mappa è davvero pronta.
 * Uso lo stesso ViewModel delle altre schermate, così sulla mappa valgono anche i filtri.
 */
class ParkingMapFragment : Fragment(), OnMapReadyCallback {

    private val viewModel: ParkingViewModel by activityViewModels {
        ParkingViewModelFactory((requireActivity().application as ParkingApplication).repository)
    }

    private var googleMap: GoogleMap? = null

    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(requireContext()) }

    // la telecamera si posiziona una sola volta per apertura della mappa: prima sulla posizione
    // dell'utente, solo se non è disponibile sul primo parcheggio della lista
    private var cameraPositioned = false
    private var userLocationResolved = false

    // permesso posizione per mostrare il puntino blu "tu sei qui"
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) enableMyLocation() else onUserLocationUnavailable()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_parking_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // chiedo alla SupportMapFragment di prepararsi; mi richiamerà onMapReady
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    // chiamato quando la mappa è pronta da usare
    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        // il fragment può restare nel back stack mentre la vista viene ricreata: riparto da zero
        cameraPositioned = false
        userLocationResolved = false

        // attivo la posizione attuale del telefono (chiedendo il permesso se manca)
        if (hasLocationPermission()) {
            enableMyLocation()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        // osservo la lista (già filtrata dal ViewModel) e disegno un marker per ognuno
        viewModel.parkingList.observe(viewLifecycleOwner) { parkings ->
            googleMap?.clear() // pulisco prima, altrimenti i marker si accumulano

            if (parkings.isNotEmpty()) {
                parkings.forEach { parking ->
                    val pos = LatLng(parking.lat, parking.lon)
                    googleMap?.addMarker(
                        MarkerOptions()
                            .position(pos)
                            .title(parking.name)
                            .snippet(parking.city)
                    )
                }
                // se la posizione dell'utente non c'è, parto dal primo parcheggio
                if (userLocationResolved) moveToFirstParking(parkings)
            }
        }

        // tap sul fumetto del marker -> apro il dettaglio di quel parcheggio
        googleMap?.setOnInfoWindowClickListener { marker ->
            // ritrovo il parcheggio dal titolo del marker (cioè il nome)
            val parking = viewModel.parkingList.value?.find { it.name == marker.title }
            if (parking != null) {
                val action = ParkingMapFragmentDirections.actionParkingMapFragmentToParkingDetailFragment(parking)
                findNavController().navigate(action)
            }
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    // @SuppressLint: so che serve il permesso, ma l'ho già controllato in hasLocationPermission()
    @SuppressLint("MissingPermission")
    private fun enableMyLocation() {
        if (!hasLocationPermission()) return
        googleMap?.isMyLocationEnabled = true                       // puntino blu
        googleMap?.uiSettings?.isMyLocationButtonEnabled = true     // bottone "centra su di me"
        centerOnUserLocation()
    }

    // lastLocation è spesso null (cache vuota): in quel caso chiedo una posizione "fresca"
    @SuppressLint("MissingPermission")
    private fun centerOnUserLocation() {
        fusedClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) onUserLocation(location) else requestFreshLocation()
        }.addOnFailureListener {
            requestFreshLocation()
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestFreshLocation() {
        fusedClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            CancellationTokenSource().token
        ).addOnSuccessListener { fresh ->
            if (fresh != null) onUserLocation(fresh) else onUserLocationUnavailable()
        }.addOnFailureListener {
            onUserLocationUnavailable()
        }
    }

    private fun onUserLocation(location: Location) {
        userLocationResolved = true
        // la risposta è asincrona: se nel frattempo la vista è stata distrutta non tocco la mappa
        if (view == null || cameraPositioned) return
        cameraPositioned = true
        val me = LatLng(location.latitude, location.longitude)
        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(me, 14f))
    }

    // niente permesso o niente posizione: ripiego sul primo parcheggio (se la lista è già
    // arrivata; altrimenti ci pensa l'observer, perché userLocationResolved ora è true)
    private fun onUserLocationUnavailable() {
        userLocationResolved = true
        viewModel.parkingList.value?.let { moveToFirstParking(it) }
    }

    // sposto la telecamera sul primo parcheggio così non parto in mezzo all'oceano
    private fun moveToFirstParking(parkings: List<ParkingEntity>) {
        if (view == null || cameraPositioned || parkings.isEmpty()) return
        cameraPositioned = true
        val firstParking = LatLng(parkings[0].lat, parkings[0].lon)
        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(firstParking, 13f))
    }
}

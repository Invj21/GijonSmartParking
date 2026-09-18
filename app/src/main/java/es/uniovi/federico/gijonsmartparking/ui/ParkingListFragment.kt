package es.uniovi.federico.gijonsmartparking.ui

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.gms.location.LocationServices
import es.uniovi.federico.gijonsmartparking.ParkingApplication
import es.uniovi.federico.gijonsmartparking.data.ParkingEntity
import es.uniovi.federico.gijonsmartparking.databinding.FragmentParkingListBinding
import androidx.navigation.fragment.findNavController
import es.uniovi.federico.gijonsmartparking.R
import es.uniovi.federico.gijonsmartparking.utils.OpeningHoursUtil
import java.text.SimpleDateFormat
import java.util.Calendar

/**
 * Mostra la lista dei parcheggi nel RecyclerView. È una sola schermata che si comporta
 * in 3 modi diversi a seconda degli argomenti che arrivano dalla Home (Safe Args):
 *  - normale: tutti i parcheggi
 *  - availableOnly: solo quelli aperti (con possibilità di scegliere data/ora futura)
 *  - favoritesOnly: solo i preferiti
 * Qui c'è anche la barra di ricerca, il pulsante filtri e il filtro "vicino a me" (GPS).
 */
class ParkingListFragment : Fragment() {

    private var _binding: FragmentParkingListBinding? = null
    private val binding get() = _binding!!

    // argomenti di navigazione (availableOnly / favoritesOnly)
    private val args: ParkingListFragmentArgs by navArgs()

    // momento scelto nel filtro data/ora della sezione "disponibili" (null = adesso)
    private var selectedDateTime: Calendar? = null

    // stesso ViewModel di Home e Mappa (activityViewModels) -> dati e filtri condivisi
    private val viewModel: ParkingViewModel by activityViewModels {
        ParkingViewModelFactory((requireActivity().application as ParkingApplication).repository)
    }

    // client dei Google Play Services per ottenere la posizione
    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(requireContext())
    }

    // ACCESS_FINE_LOCATION è un permesso "pericoloso": va chiesto a runtime con la ActivityResult API
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            fetchUserLocation()
        } else {
            // se l'utente nega, il filtro "vicino a me" semplicemente non restringe nulla
            Toast.makeText(requireContext(), R.string.location_permission_denied, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentParkingListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // creo l'adapter passandogli cosa fare al tap sulla card e sul cuore
        val adapter = ParkingAdapter(
            onItemClicked = { parking ->
                // apro il dettaglio passando il parcheggio con Safe Args
                val action = ParkingListFragmentDirections.actionParkingListFragmentToParkingDetailFragment(parking)
                findNavController().navigate(action)
            },
            onFavoriteClicked = { parking ->
                // inverto lo stato del preferito; il DB cambia e la lista si aggiorna da sola
                viewModel.setFavorite(parking.id, !parking.isFavorite)
            }
        )

        binding.rvParking.adapter = adapter
        // numero di colonne preso dalle risorse: 1 su telefono, 2 su tablet (values-sw600dp)
        binding.rvParking.layoutManager =
            GridLayoutManager(requireContext(), resources.getInteger(R.integer.parking_columns))

        val availableOnly = args.availableOnly
        val favoritesOnly = args.favoritesOnly
        // l'intestazione col conteggio + selettore data/ora serve solo in modalità "disponibili"
        binding.availableHeader.visibility = if (availableOnly) View.VISIBLE else View.GONE

        // tengo l'ultima lista arrivata, così posso rifiltrarla quando cambio data/ora
        var lastList: List<ParkingEntity> = emptyList()

        // funzione locale che decide cosa mostrare in base alla modalità
        fun render() {
            val shown = when {
                favoritesOnly -> lastList.filter { it.isFavorite }
                availableOnly -> {
                    val time = selectedDateTime
                    // se non ho scelto un orario uso "adesso", altrimenti il momento scelto
                    if (time == null) lastList.filter { OpeningHoursUtil.isOpenNow(it.openingHours) }
                    else lastList.filter { OpeningHoursUtil.isOpenAt(it.openingHours, time) }
                }
                else -> lastList
            }
            adapter.submitList(shown)

            if (availableOnly) {
                val time = selectedDateTime
                // banner: cambia testo se sto guardando "ora" o un momento futuro
                binding.tvAvailableBanner.text = if (time == null) {
                    getString(R.string.available_banner, shown.size)
                } else {
                    getString(R.string.available_banner_at, shown.size, formatDateTime(time))
                }
                binding.btnPickDateTime.text =
                    if (time == null) getString(R.string.datetime_now) else formatDateTime(time)
            }

            // messaggio "nessun preferito" quando la lista preferiti è vuota
            if (shown.isEmpty() && favoritesOnly) {
                binding.tvEmpty.visibility = View.VISIBLE
                binding.tvEmpty.text = getString(R.string.favorites_empty)
            } else {
                binding.tvEmpty.visibility = View.GONE
            }
        }

        // mi metto in ascolto della lista del ViewModel: quando cambia, rifaccio il render
        viewModel.parkingList.observe(viewLifecycleOwner) { lista ->
            lastList = lista
            render()
        }

        if (availableOnly) {
            binding.btnPickDateTime.text = getString(R.string.datetime_now)
            // tap sul pulsante data/ora -> apro i picker e poi rifiltro
            binding.btnPickDateTime.setOnClickListener {
                pickDateTime { picked ->
                    selectedDateTime = picked
                    render()
                }
            }
        }

        // rotellina di caricamento mentre il repository scarica
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        binding.fabFindMyCar.setOnClickListener {
            findNavController().navigate(R.id.action_parkingListFragment_to_findMyCarFragment)
        }

        // pulsante filtri -> apro il bottom sheet (provo anche a prendere la posizione per "vicino a me")
        binding.btnFilter.setOnClickListener {
            ensureLocation()
            FilterBottomSheetFragment().show(parentFragmentManager, FilterBottomSheetFragment.TAG)
        }

        // i dati li scarica già la Home (ViewModel condiviso), qui non rifaccio la rete

        // provo subito a ottenere la posizione se il permesso c'è già
        ensureLocation()

        // barra di ricerca: a ogni lettera aggiorno la query nel ViewModel (filtro reattivo)
        binding.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                viewModel.setSearchQuery(query ?: "")
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setSearchQuery(newText ?: "")
                return true
            }
        })
    }

    /** Apro prima il calendario e poi l'orologio; blocco le date passate (minDate = adesso). */
    private fun pickDateTime(onPicked: (Calendar) -> Unit) {
        val now = Calendar.getInstance()
        val dialog = DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                TimePickerDialog(
                    requireContext(),
                    { _, hour, minute ->
                        val chosen = Calendar.getInstance().apply {
                            set(year, month, day, hour, minute, 0)
                        }
                        onPicked(chosen)
                    },
                    now.get(Calendar.HOUR_OF_DAY),
                    now.get(Calendar.MINUTE),
                    true
                ).show()
            },
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH),
            now.get(Calendar.DAY_OF_MONTH)
        )
        dialog.datePicker.minDate = now.timeInMillis
        dialog.show()
    }

    // formatto data+ora nella lingua attiva dell'app
    private fun formatDateTime(time: Calendar): String {
        val locale = resources.configuration.locales[0]
        return SimpleDateFormat("EEE d MMM, HH:mm", locale).format(time.time)
    }

    /** Se ho già il permesso prendo la posizione, altrimenti la chiedo all'utente. */
    private fun ensureLocation() {
        val granted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            fetchUserLocation()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun fetchUserLocation() {
        // il permesso l'ho già controllato prima di arrivare qui
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    // passo la posizione al ViewModel, che la userà nel filtro del raggio
                    viewModel.setUserLocation(location.latitude, location.longitude)
                } else {
                    Toast.makeText(requireContext(), R.string.location_unavailable, Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: SecurityException) {
            // non dovrebbe succedere, ma per sicurezza lo gestisco
            e.printStackTrace()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

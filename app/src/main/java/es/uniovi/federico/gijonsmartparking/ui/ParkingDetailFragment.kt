package es.uniovi.federico.gijonsmartparking.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.navArgs
import es.uniovi.federico.gijonsmartparking.ParkingApplication
import es.uniovi.federico.gijonsmartparking.databinding.FragmentParkingDetailBinding
import es.uniovi.federico.gijonsmartparking.R
import es.uniovi.federico.gijonsmartparking.utils.ParkingLabels
import android.content.Intent
import android.net.Uri

/**
 * Schermata di dettaglio di un singolo parcheggio. Riceve l'oggetto ParkingEntity come
 * argomento di navigazione (Safe Args), quindi qui non serve rileggere dal database.
 * Mostra tutte le info tradotte e permette di salvarlo tra i preferiti o aprirlo nelle mappe.
 */
class ParkingDetailFragment : Fragment() {

    private var _binding: FragmentParkingDetailBinding? = null
    private val binding get() = _binding!!

    // il parcheggio arrivato dalla lista/mappa tramite Safe Args
    private val args: ParkingDetailFragmentArgs by navArgs()

    // mi serve solo per salvare il preferito nel DB (operazione condivisa col resto)
    private val viewModel: ParkingViewModel by activityViewModels {
        ParkingViewModelFactory((requireActivity().application as ParkingApplication).repository)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentParkingDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val parking = args.parking

        // se è una moto senza nome metto il nome generico tradotto
        binding.tvDetailName.text =
            if (parking.name.isBlank()) getString(R.string.moto_parking_generic) else parking.name
        binding.tvDetailCity.text = getString(R.string.label_city, parking.city)

        // Preferito: parto dallo stato che mi è arrivato e lo aggiorno al click.
        // (args.parking è una "foto" del momento, quindi tengo io una variabile locale)
        var isFavorite = parking.isFavorite
        updateFavoriteIcon(isFavorite)
        binding.btnFavorite.setOnClickListener {
            isFavorite = !isFavorite
            updateFavoriteIcon(isFavorite)
            viewModel.setFavorite(parking.id, isFavorite) // salvo nel database
        }

        // Costruisco il blocco di info usando ParkingLabels così è tutto tradotto.
        val ctx = requireContext()
        val fee = ParkingLabels.fee(ctx, parking.fee)
        val capacity = parking.capacity?.toString() ?: getString(R.string.not_available)
        val covered = ParkingLabels.covered(ctx, parking.covered)
        val hours = if (parking.openingHours.isBlank() || parking.openingHours == "unknown")
            getString(R.string.not_available) else parking.openingHours
        val charging = if (parking.hasCharging) getString(R.string.value_yes) else getString(R.string.value_no)
        val pink = if (parking.hasPinkParking) getString(R.string.value_yes) else getString(R.string.value_no)
        // tipo di veicolo tradotto
        val vehicle = if (parking.vehicle == "motorcycle")
            getString(R.string.vehicle_motorcycle) else getString(R.string.vehicle_car)

        // buildList: aggiungo la riga tariffa solo se c'è davvero, poi unisco tutto a capo
        val info = buildList {
            add(getString(R.string.label_vehicle, vehicle))
            add(getString(R.string.label_type, ParkingLabels.type(ctx, parking.type)))
            add(getString(R.string.label_fee, fee))
            if (parking.charge.isNotBlank()) add(getString(R.string.label_charge, parking.charge))
            add(getString(R.string.label_capacity, capacity))
            add(getString(R.string.label_covered, covered))
            add(getString(R.string.label_hours, hours))
            add(getString(R.string.label_charging, charging))
            add(getString(R.string.label_accessibility, ParkingLabels.wheelchair(ctx, parking.wheelchair)))
            add(getString(R.string.label_pink, pink))
            add(getString(R.string.label_coordinates, parking.lat, parking.lon))
        }.joinToString("\n")

        binding.tvDetailInfo.text = info

        // Apri nelle mappe: uso un Intent implicito con uri "geo:" (come visto a teoria sugli Intent),
        // così Android propone l'app di mappe installata invece di reinventare la navigazione.
        binding.btnShowMap.setOnClickListener {
            val lat = parking.lat
            val lon = parking.lon
            val label = parking.name
            val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon($label)")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        }
    }

    // cambio l'icona del cuore in base allo stato (pieno = preferito)
    private fun updateFavoriteIcon(isFavorite: Boolean) {
        binding.btnFavorite.setIconResource(
            if (isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

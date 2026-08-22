package es.uniovi.federico.gijonsmartparking.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import es.uniovi.federico.gijonsmartparking.ParkingApplication
import es.uniovi.federico.gijonsmartparking.R
import es.uniovi.federico.gijonsmartparking.databinding.FragmentHomeBinding
import es.uniovi.federico.gijonsmartparking.utils.OpeningHoursUtil

/**
 * Schermata iniziale dell'app. Da qui scelgo cosa fare con tre card (tutti i parcheggi /
 * disponibili ora / preferiti) e posso cambiare lingua col pulsante bandiera.
 * Mostra anche dei contatori rapidi (totale, aperti adesso, preferiti).
 */
class HomeFragment : Fragment() {

    // ViewBinding: invece di findViewById accedo alle View tramite "binding".
    // Il pattern _binding/binding serve a liberarlo in onDestroyView ed evitare memory leak.
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // activityViewModels: condivido lo STESSO ViewModel con lista e mappa (scope Activity),
    // così i dati scaricati e i filtri sono comuni a tutte le schermate.
    private val viewModel: ParkingViewModel by activityViewModels {
        ParkingViewModelFactory((requireActivity().application as ParkingApplication).repository)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // appena entro in home faccio partire il download (se non già fatto)
        viewModel.refreshData()

        // Card 1 -> lista completa. Navigo con Safe Args passando i due flag a false.
        binding.cardAllParkings.setOnClickListener {
            findNavController().navigate(
                HomeFragmentDirections.actionHomeFragmentToParkingListFragment(
                    availableOnly = false, favoritesOnly = false
                )
            )
        }

        // Card 2 -> solo i parcheggi aperti adesso
        binding.cardAvailable.setOnClickListener {
            findNavController().navigate(
                HomeFragmentDirections.actionHomeFragmentToParkingListFragment(
                    availableOnly = true, favoritesOnly = false
                )
            )
        }

        // Card 3 -> solo i preferiti
        binding.cardFavorites.setOnClickListener {
            findNavController().navigate(
                HomeFragmentDirections.actionHomeFragmentToParkingListFragment(
                    availableOnly = false, favoritesOnly = true
                )
            )
        }

        // observe: ogni volta che la lista cambia aggiorno i tre contatori in home
        viewModel.parkingList.observe(viewLifecycleOwner) { list ->
            binding.tvTotalCount.text = "${list.size} ${getString(R.string.parking_count_label)}"
            val openNow = list.count { OpeningHoursUtil.isOpenNow(it.openingHours) }
            binding.tvAvailableCount.text = getString(R.string.home_available_count, openNow)
            val favorites = list.count { it.isFavorite }
            binding.tvFavoritesCount.text = getString(R.string.home_favorites_count, favorites)
        }

        setupLanguageButton()

        // Logout: cancello il token e torno alla schermata di login, svuotando tutto
        // il back stack (nav_graph = id del grafo radice) così non si può tornare indietro.
        binding.btnLogout.setOnClickListener {
            (requireActivity().application as ParkingApplication).authRepository.logout()
            findNavController().navigate(
                R.id.loginFragment,
                null,
                NavOptions.Builder().setPopUpTo(R.id.nav_graph, true).build()
            )
        }
    }

    /**
     * Cambio lingua per-app (come nelle slide "Selección de idiomas por aplicación").
     * Sul pulsante mostro la bandiera della lingua ATTIVA; al click passo all'altra con
     * AppCompatDelegate.setApplicationLocales, che ricrea l'Activity con la nuova lingua.
     */
    private fun setupLanguageButton() {
        val isItalian = AppCompatDelegate.getApplicationLocales().toLanguageTags().startsWith("it")
        binding.btnLanguage.setIconResource(
            if (isItalian) R.drawable.ic_flag_it else R.drawable.ic_flag_gb
        )

        binding.btnLanguage.setOnClickListener {
            val nowItalian = AppCompatDelegate.getApplicationLocales().toLanguageTags().startsWith("it")
            val newTag = if (nowItalian) "en" else "it"
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(newTag))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // libero il binding: la View non esiste più
    }
}

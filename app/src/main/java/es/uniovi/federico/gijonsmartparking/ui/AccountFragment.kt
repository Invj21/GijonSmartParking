package es.uniovi.federico.gijonsmartparking.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.FileProvider
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import es.uniovi.federico.gijonsmartparking.ParkingApplication
import es.uniovi.federico.gijonsmartparking.R
import es.uniovi.federico.gijonsmartparking.data.AccountDto
import es.uniovi.federico.gijonsmartparking.data.NetworkModule
import es.uniovi.federico.gijonsmartparking.databinding.FragmentAccountBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Schermata Account: foto profilo, dati personali (nome/cognome/indirizzo di casa),
 * cambio lingua (spostato da Home) e gestione dell'account (logout, eliminazione).
 */
class AccountFragment : Fragment() {

    private var _binding: FragmentAccountBinding? = null
    private val binding get() = _binding!!

    private val authViewModel: AuthViewModel by viewModels {
        AuthViewModelFactory((requireActivity().application as ParkingApplication).authRepository)
    }

    // activityViewModels: stesso ParkingViewModel di Home/lista, mi serve solo per pulire
    // preferiti/posizione auto in locale quando l'account viene eliminato.
    private val parkingViewModel: ParkingViewModel by activityViewModels {
        ParkingViewModelFactory((requireActivity().application as ParkingApplication).repository)
    }

    // Foto scattata/scelta ma non ancora salvata sul backend: la mostro subito,
    // la mando solo quando l'utente tocca "Salva".
    private var pendingPhotoFile: File? = null

    // Se l'utente annulla lo scatto, il file vuoto creato in anticipo da createImageFile()
    // va cancellato e il riferimento azzerato, altrimenti un "Salva" successivo caricherebbe
    // quel file da 0 byte come foto profilo.
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            pendingPhotoFile?.let { showPendingPhoto(it) }
        } else {
            pendingPhotoFile?.delete()
            pendingPhotoFile = null
        }
    }

    private val pickPhotoLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val file = copyUriToLocalFile(uri)
            if (file != null) {
                pendingPhotoFile = file
                showPendingPhoto(file)
            }
        }
    }

    // Autocomplete di Google Places: l'utente sceglie da una lista di indirizzi reali invece
    // di scriverlo a mano, così è garantito che Google Maps lo riconosca.
    private val addressAutocompleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data ?: return@registerForActivityResult
        if (result.resultCode == Activity.RESULT_OK) {
            val place = Autocomplete.getPlaceFromIntent(data)
            binding.etHomeAddress.setText(place.address.orEmpty())
        } else {
            val status = Autocomplete.getStatusFromIntent(data)
            status.statusMessage?.let { Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show() }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvAccountEmail.text = authViewModel.loggedInEmail()

        setupLanguageButton()

        binding.profilePhotoContainer.setOnClickListener { showPhotoSourceDialog() }

        binding.etHomeAddress.setOnClickListener { launchAddressAutocomplete() }
        binding.tilHomeAddress.setEndIconOnClickListener { launchAddressAutocomplete() }

        binding.btnSaveProfile.setOnClickListener {
            val firstName = binding.etFirstName.text?.toString()?.trim().orEmpty()
            val lastName = binding.etLastName.text?.toString()?.trim().orEmpty()
            val homeAddress = binding.etHomeAddress.text?.toString()?.trim().orEmpty()
            authViewModel.saveProfile(firstName, lastName, homeAddress, pendingPhotoFile)
        }

        // Logout: cancello il token e torno alla schermata di login, svuotando tutto
        // il back stack (nav_graph = id del grafo radice) così non si può tornare indietro.
        binding.btnLogout.setOnClickListener {
            authViewModel.logout()
            findNavController().navigate(
                R.id.loginFragment,
                null,
                NavOptions.Builder().setPopUpTo(R.id.nav_graph, true).build()
            )
        }

        binding.btnDeleteAccount.setOnClickListener { confirmDeleteAccount() }

        authViewModel.profile.observe(viewLifecycleOwner) { profile -> applyProfile(profile) }
        authViewModel.loadProfile()

        authViewModel.authState.observe(viewLifecycleOwner) { state ->
            binding.btnDeleteAccount.isEnabled = state !is AuthUiState.Loading
            binding.btnSaveProfile.isEnabled = state !is AuthUiState.Loading
            when (state) {
                is AuthUiState.AccountDeleted -> {
                    // niente più account sul backend: pulisco anche i dati locali legati
                    // all'utente (preferiti, posizione auto) prima di tornare al login.
                    parkingViewModel.clearLocalUserData()
                    findNavController().navigate(
                        R.id.loginFragment,
                        null,
                        NavOptions.Builder().setPopUpTo(R.id.nav_graph, true).build()
                    )
                }
                is AuthUiState.ProfileSaved -> {
                    pendingPhotoFile = null // ora la foto "vera" è quella tornata dal backend
                    Toast.makeText(requireContext(), R.string.toast_profile_saved, Toast.LENGTH_SHORT).show()
                }
                is AuthUiState.Error -> {
                    Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                }
                else -> Unit
            }
        }
    }

    /** Riempie i campi con i dati del profilo scaricati dal backend. */
    private fun applyProfile(profile: AccountDto?) {
        profile ?: return
        binding.etFirstName.setText(profile.firstName.orEmpty())
        binding.etLastName.setText(profile.lastName.orEmpty())
        binding.etHomeAddress.setText(profile.homeAddress.orEmpty())
        updateGoHomeButton(profile.homeAddress)

        // Se c'è già una foto in sospeso (appena scattata/scelta) non la sovrascrivo con
        // quella del server: è più recente di quello che ha appena risposto il backend.
        if (pendingPhotoFile == null) {
            val photoUrl = profile.photoUrl?.let { NetworkModule.resolveBackendUrl(it) }
            if (photoUrl != null) {
                Glide.with(this).load(photoUrl).placeholder(R.drawable.ic_nav_account).circleCrop()
                    .into(binding.ivProfilePhoto)
            }
        }
    }

    /** "Portami a casa": visibile solo se c'è un indirizzo salvato, apre Google Maps in navigazione. */
    private fun updateGoHomeButton(homeAddress: String?) {
        if (homeAddress.isNullOrBlank()) {
            binding.btnGoHome.visibility = View.GONE
            return
        }
        binding.btnGoHome.visibility = View.VISIBLE
        binding.btnGoHome.setOnClickListener {
            val uri = Uri.parse("google.navigation:q=${Uri.encode(homeAddress)}")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.setPackage("com.google.android.apps.maps")
            startActivity(intent)
        }
    }

    private fun launchAddressAutocomplete() {
        val fields = listOf(Place.Field.ADDRESS)
        val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
            .build(requireActivity())
        addressAutocompleteLauncher.launch(intent)
    }

    private fun showPhotoSourceDialog() {
        val options = arrayOf(
            getString(R.string.account_photo_source_camera),
            getString(R.string.account_photo_source_gallery)
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.account_change_photo)
            .setItems(options) { _, which ->
                if (which == 0) dispatchTakePictureIntent() else dispatchPickPhotoIntent()
            }
            .show()
    }

    private fun dispatchTakePictureIntent() {
        val photoFile: File? = try { createImageFile() } catch (ex: Exception) { null }
        photoFile?.also {
            pendingPhotoFile = it
            val photoURI: Uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", it)
            takePictureLauncher.launch(photoURI)
        }
    }

    private fun dispatchPickPhotoIntent() {
        pickPhotoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("PROFILE_${timeStamp}_", ".jpg", storageDir)
    }

    /** Copio la foto scelta dalla galleria (Uri content://) in un file locale, così la tratto
     * esattamente come uno scatto di camera (stesso [pendingPhotoFile]/upload multipart). */
    private fun copyUriToLocalFile(uri: Uri): File? {
        return try {
            val file = createImageFile()
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            file
        } catch (e: Exception) {
            null
        }
    }

    private fun showPendingPhoto(file: File) {
        Glide.with(this).load(file).circleCrop().into(binding.ivProfilePhoto)
    }

    private fun confirmDeleteAccount() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.account_delete_dialog_title)
            .setMessage(R.string.account_delete_dialog_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.account_delete_confirm) { _, _ ->
                authViewModel.deleteAccount()
            }
            .show()
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
        _binding = null
    }
}

package es.uniovi.federico.gijonsmartparking.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import es.uniovi.federico.gijonsmartparking.ParkingApplication
import es.uniovi.federico.gijonsmartparking.R
import es.uniovi.federico.gijonsmartparking.databinding.FragmentRegisterBinding
import kotlinx.coroutines.launch

/**
 * Schermata di registrazione. Il backend fa login automatico alla registrazione, quindi
 * dopo una registrazione riuscita (o un accesso con Google) vado direttamente in Home
 * invece di rimandare l'utente alla schermata di accesso.
 */
class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AuthViewModel by viewModels {
        AuthViewModelFactory((requireActivity().application as ParkingApplication).authRepository)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnRegister.setOnClickListener {
            val email = binding.etEmail.text?.toString()?.trim().orEmpty()
            val password = binding.etPassword.text?.toString().orEmpty()

            when {
                email.isEmpty() || password.isEmpty() ->
                    showError(getString(R.string.auth_error_empty_fields))
                password.length < 6 ->
                    showError(getString(R.string.auth_error_password_length))
                else ->
                    viewModel.register(email, password)
            }
        }

        binding.btnGoLogin.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnGoogleSignIn.setOnClickListener { signInWithGoogle() }

        viewModel.authState.observe(viewLifecycleOwner) { state ->
            binding.progress.visibility = if (state is AuthUiState.Loading) View.VISIBLE else View.GONE
            binding.btnRegister.isEnabled = state !is AuthUiState.Loading
            binding.btnGoogleSignIn.isEnabled = state !is AuthUiState.Loading

            when (state) {
                is AuthUiState.RegisterSuccess, is AuthUiState.LoginSuccess -> {
                    if (state is AuthUiState.RegisterSuccess) {
                        Toast.makeText(requireContext(), R.string.auth_register_success, Toast.LENGTH_SHORT).show()
                    }
                    // Sia la registrazione sia l'accesso con Google autenticano subito
                    // l'utente: vado direttamente in Home svuotando tutto il back stack
                    // (login + registrazione), niente ritorno alla schermata di accesso.
                    findNavController().navigate(
                        R.id.homeFragment,
                        null,
                        NavOptions.Builder().setPopUpTo(R.id.nav_graph, true).build()
                    )
                }
                is AuthUiState.Error -> showError(state.message)
                else -> Unit
            }
        }
    }

    /**
     * Apro il selettore account di Credential Manager, prendo l'ID token e lo mando al
     * ViewModel: il backend crea l'utente al primo accesso (o fa login se esiste già).
     */
    private fun signInWithGoogle() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val googleCredential = GoogleSignInHelper.signIn(
                    requireActivity(), getString(R.string.default_web_client_id)
                )
                viewModel.loginWithGoogle(googleCredential.idToken)
            } catch (e: GetCredentialCancellationException) {
                // l'utente ha chiuso il selettore account: nessun errore da mostrare
            } catch (e: GetCredentialException) {
                showError(getString(R.string.auth_error_google_generic))
            }
        }
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

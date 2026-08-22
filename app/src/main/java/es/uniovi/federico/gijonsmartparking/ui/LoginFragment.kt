package es.uniovi.federico.gijonsmartparking.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import es.uniovi.federico.gijonsmartparking.ParkingApplication
import es.uniovi.federico.gijonsmartparking.R
import es.uniovi.federico.gijonsmartparking.databinding.FragmentLoginBinding
import kotlinx.coroutines.launch

/**
 * Schermata di login: è la start destination del nav_graph quando non c'è un token
 * salvato (vedi MainActivity.onCreate). Stesso schema ViewBinding/ViewModel degli
 * altri Fragment (HomeFragment, ecc.).
 */
class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    // viewModels (non activityViewModels): login e registrazione non condividono stato,
    // ognuno ha il proprio ViewModel effimero.
    private val viewModel: AuthViewModel by viewModels {
        AuthViewModelFactory((requireActivity().application as ParkingApplication).authRepository)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text?.toString()?.trim().orEmpty()
            val password = binding.etPassword.text?.toString().orEmpty()

            if (email.isEmpty() || password.isEmpty()) {
                showError(getString(R.string.auth_error_empty_fields))
                return@setOnClickListener
            }
            viewModel.login(email, password)
        }

        binding.btnGoRegister.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
        }

        binding.btnGoogleSignIn.setOnClickListener { signInWithGoogle() }

        viewModel.authState.observe(viewLifecycleOwner) { state ->
            binding.progress.visibility = if (state is AuthUiState.Loading) View.VISIBLE else View.GONE
            binding.btnLogin.isEnabled = state !is AuthUiState.Loading
            binding.btnGoogleSignIn.isEnabled = state !is AuthUiState.Loading

            when (state) {
                is AuthUiState.LoginSuccess -> {
                    // tolgo loginFragment dal back stack: dopo il login il tasto Indietro
                    // non deve poter tornare alla schermata di accesso
                    findNavController().navigate(
                        R.id.homeFragment,
                        null,
                        NavOptions.Builder().setPopUpTo(R.id.loginFragment, true).build()
                    )
                }
                is AuthUiState.Error -> showError(state.message)
                else -> Unit
            }
        }
    }

    /**
     * Apro il selettore account di Credential Manager, prendo l'ID token e lo mando al
     * ViewModel: il backend fa login o registrazione a seconda che l'email esista già.
     */
    private fun signInWithGoogle() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val googleCredential = GoogleSignInHelper.signIn(
                    requireActivity(), getString(R.string.default_web_client_id)
                )
                viewModel.loginWithGoogle(googleCredential.idToken, googleCredential.id)
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

package es.uniovi.federico.gijonsmartparking.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import es.uniovi.federico.gijonsmartparking.data.AccountDto
import es.uniovi.federico.gijonsmartparking.data.AuthRepository
import kotlinx.coroutines.launch
import java.io.File

/**
 * ViewModel di login/registrazione: stesso pattern MVVM di ParkingViewModel.
 * Non valida i campi (lo fa il Fragment, che ha accesso alle string resources):
 * qui arrivano già email/password non vuote, il ViewModel pensa solo alla chiamata di rete.
 */
class AuthViewModel(private val repository: AuthRepository) : ViewModel() {

    private val _authState = MutableLiveData<AuthUiState>(AuthUiState.Idle)
    val authState: LiveData<AuthUiState> get() = _authState

    // Profilo (sezione Account): separato da authState perché il caricamento iniziale
    // non deve mostrare la rotellina/errori di login sul resto della schermata.
    private val _profile = MutableLiveData<AccountDto?>()
    val profile: LiveData<AccountDto?> get() = _profile

    fun login(email: String, password: String) {
        _authState.value = AuthUiState.Loading
        viewModelScope.launch {
            val result = repository.login(email, password)
            _authState.value = result.fold(
                onSuccess = { AuthUiState.LoginSuccess },
                onFailure = { AuthUiState.Error(it.message ?: "Errore") }
            )
        }
    }

    fun register(email: String, password: String) {
        _authState.value = AuthUiState.Loading
        viewModelScope.launch {
            val result = repository.register(email, password)
            _authState.value = result.fold(
                onSuccess = { AuthUiState.RegisterSuccess },
                onFailure = { AuthUiState.Error(it.message ?: "Errore") }
            )
        }
    }

    /** Login/registrazione con Google: find-or-create sul backend, va sempre in LoginSuccess. */
    fun loginWithGoogle(idToken: String) {
        _authState.value = AuthUiState.Loading
        viewModelScope.launch {
            val result = repository.loginWithGoogle(idToken)
            _authState.value = result.fold(
                onSuccess = { AuthUiState.LoginSuccess },
                onFailure = { AuthUiState.Error(it.message ?: "Errore") }
            )
        }
    }

    fun logout() {
        repository.logout()
    }

    fun loggedInEmail(): String? = repository.loggedInEmail()

    /** Elimina l'account (backend + sessione locale). I dati locali (preferiti/posizione auto) li pulisce il chiamante. */
    fun deleteAccount() {
        _authState.value = AuthUiState.Loading
        viewModelScope.launch {
            val result = repository.deleteAccount()
            _authState.value = result.fold(
                onSuccess = { AuthUiState.AccountDeleted },
                onFailure = { AuthUiState.Error(it.message ?: "Errore") }
            )
        }
    }

    /** Carica il profilo (nome, cognome, indirizzo, foto) all'apertura della sezione Account. */
    fun loadProfile() {
        viewModelScope.launch {
            repository.getAccount().onSuccess { _profile.value = it }
        }
    }

    fun saveProfile(firstName: String, lastName: String, homeAddress: String, photoFile: File?) {
        _authState.value = AuthUiState.Loading
        viewModelScope.launch {
            val result = repository.updateAccount(firstName, lastName, homeAddress, photoFile)
            _authState.value = result.fold(
                onSuccess = { _profile.value = it; AuthUiState.ProfileSaved },
                onFailure = { AuthUiState.Error(it.message ?: "Errore") }
            )
        }
    }
}

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    object LoginSuccess : AuthUiState()
    object RegisterSuccess : AuthUiState()
    object AccountDeleted : AuthUiState()
    object ProfileSaved : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

class AuthViewModelFactory(private val repository: AuthRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

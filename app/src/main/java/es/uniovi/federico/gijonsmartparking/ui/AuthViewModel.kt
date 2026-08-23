package es.uniovi.federico.gijonsmartparking.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import es.uniovi.federico.gijonsmartparking.data.AuthRepository
import kotlinx.coroutines.launch

/**
 * ViewModel di login/registrazione: stesso pattern MVVM di ParkingViewModel.
 * Non valida i campi (lo fa il Fragment, che ha accesso alle string resources):
 * qui arrivano già email/password non vuote, il ViewModel pensa solo alla chiamata di rete.
 */
class AuthViewModel(private val repository: AuthRepository) : ViewModel() {

    private val _authState = MutableLiveData<AuthUiState>(AuthUiState.Idle)
    val authState: LiveData<AuthUiState> get() = _authState

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
}

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    object LoginSuccess : AuthUiState()
    object RegisterSuccess : AuthUiState()
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

package es.uniovi.federico.gijonsmartparking.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.HttpException

/**
 * Repository dell'autenticazione: stesso ruolo di ParkingRepository ma per login/registrazione.
 * Il ViewModel non parla mai direttamente con Retrofit o con TokenManager: passa sempre di qui.
 */
class AuthRepository(
    private val backendApiService: BackendApiService,
    private val tokenManager: TokenManager
) {

    suspend fun register(email: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Il backend fa login automatico alla registrazione (torna già un token):
            // l'utente entra direttamente, non deve rifare login con le stesse credenziali.
            val response = backendApiService.registerUser(RegisterRequest(email, password))
            tokenManager.saveSession(response.token, email)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(extractErrorMessage(e)))
        }
    }

    suspend fun login(email: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = backendApiService.loginUser(LoginRequest(email, password))
            tokenManager.saveSession(response.token, email)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(extractErrorMessage(e)))
        }
    }

    /**
     * Login/registrazione rapida con Google: idToken arriva dal login via browser
     * (GoogleSignInHelper/AppAuth), qui lo mando al backend che lo riverifica con Google
     * e crea l'utente al primo accesso (find-or-create). Il backend restituisce anche
     * l'email: da un ID token grezzo il client non avrebbe altrimenti modo di saperla
     * senza decodificarlo da solo.
     */
    suspend fun loginWithGoogle(idToken: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = backendApiService.loginWithGoogle(GoogleAuthRequest(idToken))
            tokenManager.saveSession(response.token, response.email)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(extractErrorMessage(e)))
        }
    }

    fun isLoggedIn(): Boolean = tokenManager.hasToken()

    fun loggedInEmail(): String? = tokenManager.getEmail()

    fun logout() = tokenManager.clearSession()

    /** Il backend risponde sempre con {"error": "..."} sui codici di errore (vedi backend/app.py). */
    private fun extractErrorMessage(e: Exception): String {
        if (e is HttpException) {
            val body = e.response()?.errorBody()?.string()
            val serverMessage = body?.let { runCatching { JSONObject(it).optString("error") }.getOrNull() }
            if (!serverMessage.isNullOrBlank()) return serverMessage
        }
        return e.message ?: "Errore di rete"
    }
}

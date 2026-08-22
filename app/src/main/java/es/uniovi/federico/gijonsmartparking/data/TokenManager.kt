package es.uniovi.federico.gijonsmartparking.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Salvo il token JWT (e l'email dell'utente loggato) in SharedPreferences: è lo storage
 * locale più semplice visto a teoria, mi basta per capire "sono loggato?" e per allegare
 * il Bearer token alle chiamate verso il mio backend (vedi NetworkModule).
 */
class TokenManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveSession(token: String, email: String) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_EMAIL, email)
            .apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun getEmail(): String? = prefs.getString(KEY_EMAIL, null)

    fun hasToken(): Boolean = !getToken().isNullOrBlank()

    fun clearSession() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "auth_prefs"
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_EMAIL = "user_email"
    }
}

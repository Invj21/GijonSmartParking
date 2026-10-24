package es.uniovi.federico.gijonsmartparking.ui

import android.content.Context
import es.uniovi.federico.gijonsmartparking.R

object AuthErrorMapper {

    fun localize(context: Context, message: String): String {
        val text = message.trim().lowercase()
        val resId = when {
            text == "invalid email or password" -> R.string.auth_error_invalid_credentials
            text == "email already registered" -> R.string.auth_error_email_taken
            text == "email and password are required" -> R.string.auth_error_empty_fields
            text == "invalid email address" -> R.string.auth_error_invalid_email
            text.startsWith("password must be at least") -> R.string.auth_error_password_length
            text == "invalid google token" || text.startsWith("google") -> R.string.auth_error_google_generic
            isNetworkProblem(text) -> R.string.auth_error_network
            else -> null
        }
        return if (resId != null) context.getString(resId) else message
    }

    private fun isNetworkProblem(text: String): Boolean =
        listOf("unable to resolve host", "timeout", "timed out", "failed to connect", "network", "connection")
            .any { text.contains(it) }
}

package es.uniovi.federico.gijonsmartparking.ui

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Wrapper minimo su Credential Manager per il login/registrazione veloce con Google.
 * Usato sia da LoginFragment sia da RegisterFragment: in entrambi i casi il flusso è
 * identico (il backend fa find-or-create sull'email, vedi AuthRepository.loginWithGoogle).
 */
object GoogleSignInHelper {

    /**
     * Prima provo a mostrare solo gli account Google già usati con questa app (più veloce,
     * comportamento da "accedi"); se non ce n'è nessuno (NoCredentialException), riprovo
     * mostrando il selettore con TUTTI gli account Google del dispositivo (comportamento
     * da "registrati" al primo utilizzo).
     */
    suspend fun signIn(context: Context, webClientId: String): GoogleIdTokenCredential {
        val credentialManager = CredentialManager.create(context)

        val result = try {
            credentialManager.getCredential(context, buildRequest(webClientId, onlyAuthorized = true))
        } catch (e: NoCredentialException) {
            credentialManager.getCredential(context, buildRequest(webClientId, onlyAuthorized = false))
        }

        val credential = result.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleIdTokenCredential.createFrom(credential.data)
        }
        throw IllegalStateException("Credenziale inattesa da Credential Manager")
    }

    private fun buildRequest(webClientId: String, onlyAuthorized: Boolean): GetCredentialRequest {
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(onlyAuthorized)
            .setServerClientId(webClientId)
            .build()
        return GetCredentialRequest.Builder().addCredentialOption(option).build()
    }
}

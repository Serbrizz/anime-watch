package com.example.animewatch.data.firebase

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.example.animewatch.data.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Accesso con Google tramite Credential Manager, poi scambio del token Google con una sessione Firebase.
 * Firebase conserva la sessione fra un avvio e l'altro, quindi il login serve solo la prima volta.
 */
class AuthDataSource(
    private val auth: FirebaseAuth,
    private val appContext: Context,
) {
    /** Utente collegato, `null` se nessuno. Emette subito lo stato corrente. */
    val currentUser: Flow<UserProfile?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.toProfile()) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    private fun FirebaseUser.toProfile() = UserProfile(
        uid = uid,
        name = displayName?.takeIf { it.isNotBlank() } ?: email.orEmpty().substringBefore('@'),
        // Minuscola come nelle regole (`request.auth.token.email.lower()`): è la chiave degli inviti.
        email = email.orEmpty().trim().lowercase(),
        photoUrl = photoUrl?.toString(),
    )

    /**
     * Mostra il selettore degli account Google. Serve il context di un'Activity: il selettore è una UI.
     * @return `false` se l'utente chiude il selettore senza scegliere un account.
     */
    suspend fun signInWithGoogle(activityContext: Context): Boolean {
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId()).build())
            .build()
        val credential = try {
            CredentialManager.create(activityContext).getCredential(activityContext, request).credential
        } catch (e: GetCredentialCancellationException) {
            return false
        }
        check(
            credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) { "Credenziale non supportata: ${credential.type}" }

        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        return true
    }

    suspend fun signOut() {
        auth.signOut()
        // Dimentica l'account scelto, così al prossimo accesso si può sceglierne un altro.
        runCatching { CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest()) }
            .onFailure { Log.w(TAG, "Pulizia credenziali non riuscita", it) }
    }

    /**
     * `default_web_client_id` è generato dal plugin google-services solo se `google-services.json` contiene
     * il client OAuth web, cioè dopo aver abilitato Google in Firebase Authentication. Leggerlo per nome
     * invece che con `R.string` permette di compilare anche senza, con un errore chiaro a runtime.
     */
    @SuppressLint("DiscouragedApi")
    private fun webClientId(): String {
        val id = appContext.resources.getIdentifier("default_web_client_id", "string", appContext.packageName)
        check(id != 0) {
            "client OAuth mancante. Abilita Google in Firebase Authentication, registra lo SHA-1 " +
                "e riscarica google-services.json"
        }
        return appContext.getString(id)
    }

    private companion object {
        const val TAG = "AuthDataSource"
    }
}

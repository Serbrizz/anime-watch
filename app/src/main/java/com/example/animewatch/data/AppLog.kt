package com.example.animewatch.data

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestoreException

/**
 * Log dell'app. Oltre al logcat, ogni riga va fra quelle che Crashlytics allega al prossimo crash o errore
 * non fatale (ultimi 64 KB): così dalla console si vede cosa è successo prima, anche sui telefoni degli altri.
 * Niente email o altri dati personali nei messaggi: Crashlytics li conserva legati all'UID.
 */
object AppLog {

    /** Un problema previsto (rete assente, servizio che non risponde): solo logcat e righe allegate. */
    fun warn(tag: String, message: String, e: Throwable? = null) {
        Log.w(tag, message, e)
        crashlytics { log("W/$tag: $message${e?.let { " — ${it.javaClass.simpleName}: ${it.message}" }.orEmpty()}") }
    }

    /**
     * Un errore che potrebbe non essere previsto: se non dipende dalla rete o da un servizio esterno (es. una
     * scrittura Firestore rifiutata dalle regole) arriva anche in Crashlytics come errore non fatale.
     */
    fun error(tag: String, message: String, e: Throwable) {
        warn(tag, message, e)
        if (isUnexpected(e)) crashlytics { recordException(e) }
    }

    /** Offline Firestore di solito non fallisce, ma le letture dal server (`get()`) sì: UNAVAILABLE è la rete. */
    private fun isUnexpected(e: Throwable): Boolean =
        AppErrors.kind(e) == ErrorKind.UNKNOWN &&
            (e as? FirebaseFirestoreException)?.code != FirebaseFirestoreException.Code.UNAVAILABLE

    /** Crashlytics non deve mai far fallire chi scrive un log. */
    private inline fun crashlytics(block: FirebaseCrashlytics.() -> Unit) {
        runCatching { FirebaseCrashlytics.getInstance().block() }
    }
}

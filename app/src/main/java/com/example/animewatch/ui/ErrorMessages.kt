package com.example.animewatch.ui

import android.util.Log
import com.example.animewatch.data.AppErrors
import com.example.animewatch.data.ErrorKind
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Testi d'errore per l'utente: in italiano, dicono cosa è successo e cosa fare. Il messaggio dell'eccezione
 * ("Unable to resolve host…") non si mostra mai: va nel log e, se il caso è inatteso, in Crashlytics.
 */
object ErrorMessages {

    fun recommendations(e: Throwable): String = when (report("Consigli", e)) {
        ErrorKind.OFFLINE -> "Nessuna connessione. Quando torna la rete, riprova con Aggiorna in alto."
        ErrorKind.SERVICE -> "AniList non risponde al momento. Riprova fra poco con Aggiorna in alto."
        else -> "Impossibile caricare i consigli. Riprova con Aggiorna in alto."
    }

    fun search(e: Throwable): String = when (report("Ricerca", e)) {
        ErrorKind.OFFLINE -> "Nessuna connessione: controlla la rete e riprova."
        ErrorKind.SERVICE -> "AniList non risponde al momento, riprova fra poco."
        else -> "Ricerca non riuscita, riprova."
    }

    fun updateCheck(e: Throwable): String = when (report("Controllo aggiornamenti", e)) {
        ErrorKind.OFFLINE -> "Impossibile verificare gli aggiornamenti: nessuna connessione."
        ErrorKind.SERVICE -> "GitHub non risponde al momento, riprova più tardi."
        else -> "Impossibile verificare gli aggiornamenti, riprova più tardi."
    }

    fun updateDownload(e: Throwable): String = when (report("Download aggiornamento", e)) {
        ErrorKind.OFFLINE -> "Download interrotto: controlla la connessione e riprova."
        ErrorKind.SERVICE -> "GitHub non risponde al momento, riprova più tardi."
        ErrorKind.CORRUPT_DOWNLOAD -> "Il file scaricato è danneggiato, riprova."
        ErrorKind.NO_SPACE -> "Spazio insufficiente sul telefono: libera un po' di memoria e riprova."
        ErrorKind.UNKNOWN -> "Aggiornamento non riuscito, riprova."
    }

    /**
     * Offline Firestore non fallisce (legge dalla cache), quindi un listener che si chiude con un errore è
     * sempre un caso inatteso, di solito permessi negati.
     */
    fun sync(what: String, e: Throwable): String {
        report("Sincronizzazione $what", e)
        return "Sincronizzazione $what non riuscita, riprova più tardi."
    }

    private fun report(what: String, e: Throwable): ErrorKind {
        val kind = AppErrors.kind(e)
        Log.w(TAG, "$what: errore $kind", e)
        if (kind == ErrorKind.UNKNOWN) FirebaseCrashlytics.getInstance().recordException(e)
        return kind
    }

    private const val TAG = "Errors"
}

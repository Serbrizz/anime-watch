package com.example.animewatch.data

import retrofit2.HttpException
import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ProtocolException
import java.net.SocketException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Un servizio esterno ha risposto, ma con un errore (HTTP non riuscito o errore GraphQL). */
class ServiceException(message: String) : IOException(message)

/** Il file scaricato non è quello pubblicato: vuoto, troncato o con sha256 diverso. */
class CorruptDownloadException(message: String) : IOException(message)

/** Che tipo di problema è, per dire all'utente cosa fare: il messaggio dell'eccezione è tecnico e in inglese. */
enum class ErrorKind {
    /** Rete assente, lenta o caduta a metà. */
    OFFLINE,

    /** Il servizio (AniList, GitHub) ha risposto con un errore: limite di richieste, server giù. */
    SERVICE,
    CORRUPT_DOWNLOAD,
    NO_SPACE,

    /** Inatteso: va in Crashlytics. */
    UNKNOWN,
}

object AppErrors {
    /** Guarda anche le cause: Retrofit e Firebase a volte avvolgono l'errore di rete in un altro. */
    fun kind(e: Throwable): ErrorKind {
        val chain = generateSequence(e) { it.cause?.takeIf { cause -> cause !== it } }.take(MAX_CAUSES).toList()
        return when {
            chain.any { it is CorruptDownloadException } -> ErrorKind.CORRUPT_DOWNLOAD
            chain.any { it is ServiceException || it is HttpException } -> ErrorKind.SERVICE
            chain.any { "ENOSPC" in it.message.orEmpty() } -> ErrorKind.NO_SPACE
            chain.any { it.isNetwork() } -> ErrorKind.OFFLINE
            else -> ErrorKind.UNKNOWN
        }
    }

    /**
     * Gli errori che OkHttp dà quando la connessione manca o cade: host non risolto, connessione rifiutata o
     * interrotta (SocketException), timeout (InterruptedIOException), handshake TLS fallito (Wi-Fi con login),
     * risposta troncata, e quelli di OkHttp stesso (es. stream HTTP/2 chiuso). Gli altri IOException
     * (es. scrittura su file) non sono un problema di rete.
     */
    private fun Throwable.isNetwork() =
        this is UnknownHostException || this is SocketException || this is InterruptedIOException ||
            this is SSLException || this is EOFException || this is ProtocolException ||
            (this is IOException && javaClass.name.startsWith("okhttp3."))

    private const val MAX_CAUSES = 10
}

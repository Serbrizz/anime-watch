package com.example.animewatch.data.local

import android.util.Log
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Lettura/scrittura di file JSON sotto una cartella radice (`filesDir/database`).
 *
 * La scrittura passa da un file temporaneo e poi lo rinomina: se l'app viene uccisa a metà,
 * resta il file vecchio intero invece di uno troncato.
 */
class JsonStore(private val root: File) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true // senza, i campi con valore di default (es. cachedAt) non verrebbero scritti
        prettyPrint = true // i file restano leggibili a occhio mentre si sviluppa
    }
    private val writeLock = Any()

    /** null se il file non esiste o è illeggibile (in quel caso lo si tratta come vuoto). */
    fun <T> read(path: String, serializer: KSerializer<T>): T? {
        val file = File(root, path)
        if (!file.isFile) return null
        return try {
            json.decodeFromString(serializer, file.readText())
        } catch (e: Exception) {
            Log.w(TAG, "File JSON illeggibile, lo ignoro: $path", e)
            null
        }
    }

    fun <T> write(path: String, serializer: KSerializer<T>, value: T) {
        val file = File(root, path)
        synchronized(writeLock) {
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            tmp.writeText(json.encodeToString(serializer, value))
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /** Cancella tutta la cartella "database": l'app riparte da zero. */
    fun deleteAll() {
        synchronized(writeLock) { root.deleteRecursively() }
    }

    private companion object {
        const val TAG = "JsonStore"
    }
}

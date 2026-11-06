package com.example.animewatch.data.firebase

import android.util.Log
import com.example.animewatch.data.model.AnimeList
import com.example.animewatch.data.model.WatchProgress
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Dati personali su Firestore:
 *   users/{uid}/lists/{listId}        -> AnimeList (name, createdAt, animeIds[])
 *   users/{uid}/progress/{animeId}    -> WatchProgress (watchedEpisodes[], genres[], ...)
 *
 * Le scritture NON sono attese (niente await): Firestore le applica subito alla cache
 * locale e le sincronizza appena c'è rete. Un await bloccherebbe la UI offline.
 */
class UserDataSource(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
) {
    /** Login anonimo: nessuna schermata, ma l'UID resta stabile finché non si disinstalla l'app. */
    suspend fun ensureSignedIn(): String {
        auth.currentUser?.let { return it.uid }
        return auth.signInAnonymously().await().user?.uid
            ?: error("Login anonimo senza utente")
    }

    private fun listsCol(uid: String): CollectionReference =
        db.collection("users").document(uid).collection("lists")

    private fun progressCol(uid: String): CollectionReference =
        db.collection("users").document(uid).collection("progress")

    // ---------- Liste ----------

    fun observeLists(uid: String): Flow<List<AnimeList>> = callbackFlow {
        val registration = listsCol(uid)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.mapNotNull { it.toAnimeList() }.orEmpty())
            }
        awaitClose { registration.remove() }
    }

    fun createList(uid: String, name: String) {
        listsCol(uid).add(
            mapOf(
                "name" to name,
                "createdAt" to System.currentTimeMillis(),
                "animeIds" to emptyList<Int>(),
            )
        ).logFailure("createList")
    }

    fun deleteList(uid: String, listId: String) {
        listsCol(uid).document(listId).delete().logFailure("deleteList")
    }

    fun addToList(uid: String, listId: String, animeId: Int) {
        listsCol(uid).document(listId)
            .update("animeIds", FieldValue.arrayUnion(animeId))
            .logFailure("addToList")
    }

    fun removeFromList(uid: String, listId: String, animeId: Int) {
        listsCol(uid).document(listId)
            .update("animeIds", FieldValue.arrayRemove(animeId))
            .logFailure("removeFromList")
    }

    // ---------- Progressi ----------

    fun observeProgress(uid: String): Flow<Map<Int, WatchProgress>> = callbackFlow {
        val registration = progressCol(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val map = snapshot?.documents
                ?.mapNotNull { it.toWatchProgress() }
                ?.associateBy { it.animeId }
                .orEmpty()
            trySend(map)
        }
        awaitClose { registration.remove() }
    }

    /** Salva il progresso; se non resta nessuna puntata vista il documento viene rimosso. */
    fun saveProgress(uid: String, progress: WatchProgress) {
        val ref = progressCol(uid).document(progress.animeId.toString())
        if (progress.watchedEpisodes.isEmpty()) {
            ref.delete().logFailure("deleteProgress")
        } else {
            ref.set(progress.toMap()).logFailure("saveProgress")
        }
    }

    private fun com.google.android.gms.tasks.Task<*>.logFailure(op: String) {
        addOnFailureListener { Log.e(TAG, "Scrittura Firestore fallita: $op", it) }
    }

    private companion object {
        const val TAG = "UserDataSource"
    }
}

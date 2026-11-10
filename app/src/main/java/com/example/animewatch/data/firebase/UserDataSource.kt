package com.example.animewatch.data.firebase

import android.util.Log
import com.example.animewatch.data.model.WatchProgress
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Progressi personali su Firestore (le liste stanno in [ListDataSource]):
 *   users/{uid}/progress/{animeId}    -> WatchProgress (watchedEpisodes[], genres[], ...)
 *
 * Le scritture NON sono attese (niente await): Firestore le applica subito alla cache
 * locale e le sincronizza appena c'è rete. Un await bloccherebbe la UI offline.
 */
class UserDataSource(private val db: FirebaseFirestore) {

    private fun progressCol(uid: String): CollectionReference =
        db.collection("users").document(uid).collection("progress")

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

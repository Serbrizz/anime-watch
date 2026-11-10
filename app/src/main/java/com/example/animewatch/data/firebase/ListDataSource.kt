package com.example.animewatch.data.firebase

import android.util.Log
import com.example.animewatch.data.model.AnimeList
import com.example.animewatch.data.model.ListInvite
import com.example.animewatch.data.model.ListRole
import com.example.animewatch.data.model.UserProfile
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * Liste (personali e condivise) e inviti:
 *   lists/{listId}      -> name, ownerId, createdAt, updatedAt, animeIds[], members{uid: ruolo},
 *                          memberIds[], profiles{uid: {name, photoUrl}}
 *   invites/{listId_email} -> listId, listName, role, fromUid, fromName, toEmail, createdAt
 *
 * Chi può fare cosa lo decidono le regole Firestore (firestore.rules): qui si scrive solo ciò che
 * le regole accettano. Come per i progressi, le scritture non si attendono, tranne l'accettazione
 * di un invito (vedi [acceptInvite]).
 */
class ListDataSource(private val db: FirebaseFirestore) {

    private val lists: CollectionReference get() = db.collection("lists")
    private val invites: CollectionReference get() = db.collection("invites")

    // ---------- Liste ----------

    /** Tutte le liste di cui l'utente è membro, con qualunque ruolo. */
    fun observeLists(uid: String): Flow<List<AnimeList>> = callbackFlow {
        // Ordinate qui e non con orderBy: array-contains + orderBy richiederebbe un indice composito.
        val registration = lists.whereArrayContains("memberIds", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val result = snapshot?.documents?.mapNotNull { it.toAnimeList() }.orEmpty()
                trySend(result.sortedBy { it.createdAt })
            }
        awaitClose { registration.remove() }
    }

    fun createList(owner: UserProfile, name: String) {
        lists.document().set(newListMap(owner, name, createdAt = System.currentTimeMillis(), animeIds = emptyList()))
            .logFailure("createList")
    }

    private fun newListMap(owner: UserProfile, name: String, createdAt: Long, animeIds: List<Int>) = mapOf(
        "name" to name.take(MAX_NAME),
        "ownerId" to owner.uid,
        "createdAt" to createdAt,
        "updatedAt" to System.currentTimeMillis(),
        "animeIds" to animeIds,
        "members" to mapOf(owner.uid to ListRole.OWNER.key),
        "memberIds" to listOf(owner.uid),
        "profiles" to mapOf(owner.uid to owner.toMemberMap()),
    )

    /** Solo il proprietario. Cancella anche gli inviti ancora in attesa, che altrimenti resterebbero orfani. */
    suspend fun deleteList(ownerUid: String, listId: String) {
        val pending = runCatching { pendingInvitesQuery(ownerUid, listId).get().await().documents }
            .getOrElse {
                Log.w(TAG, "Inviti della lista non letti: restano in attesa", it)
                emptyList()
            }
        db.batch().apply {
            pending.forEach { delete(it.reference) }
            delete(lists.document(listId))
        }.commit().logFailure("deleteList")
    }

    fun addToList(listId: String, animeId: Int) {
        lists.document(listId)
            .update("animeIds", FieldValue.arrayUnion(animeId), "updatedAt", System.currentTimeMillis())
            .logFailure("addToList")
    }

    fun removeFromList(listId: String, animeId: Int) {
        lists.document(listId)
            .update("animeIds", FieldValue.arrayRemove(animeId), "updatedAt", System.currentTimeMillis())
            .logFailure("removeFromList")
    }

    // ---------- Membri ----------

    /** Usato sia per "esci dalla lista" (su se stessi) sia dal proprietario per rimuovere qualcuno. */
    fun removeMember(listId: String, uid: String) {
        lists.document(listId).update(
            FieldPath.of("members", uid), FieldValue.delete(),
            "memberIds", FieldValue.arrayRemove(uid),
            FieldPath.of("profiles", uid), FieldValue.delete(),
            "updatedAt", System.currentTimeMillis(),
        ).logFailure("removeMember")
    }

    fun setMemberRole(listId: String, uid: String, role: ListRole) {
        require(role != ListRole.OWNER) { "Il proprietario non si cambia" }
        lists.document(listId).update(
            FieldPath.of("members", uid), role.key,
            "updatedAt", System.currentTimeMillis(),
        ).logFailure("setMemberRole")
    }

    // ---------- Inviti ----------

    /** Inviti ricevuti. Le regole permettono la query solo filtrando sulla propria email. */
    fun observeIncomingInvites(email: String): Flow<List<ListInvite>> =
        observeInvites(invites.whereEqualTo("toEmail", email))

    /** Inviti in attesa mandati dal proprietario per una lista. */
    fun observeOutgoingInvites(ownerUid: String, listId: String): Flow<List<ListInvite>> =
        observeInvites(pendingInvitesQuery(ownerUid, listId))

    // Due uguaglianze: Firestore unisce gli indici dei singoli campi, non serve un indice composito.
    private fun pendingInvitesQuery(ownerUid: String, listId: String): Query =
        invites.whereEqualTo("fromUid", ownerUid).whereEqualTo("listId", listId)

    private fun observeInvites(query: Query): Flow<List<ListInvite>> = callbackFlow {
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val result = snapshot?.documents?.mapNotNull { it.toListInvite() }.orEmpty()
            trySend(result.sortedByDescending { it.createdAt })
        }
        awaitClose { registration.remove() }
    }

    /** Se la persona era già stata invitata a quella lista, l'invito viene sostituito (es. cambio di ruolo). */
    fun sendInvite(list: AnimeList, from: UserProfile, toEmail: String, role: ListRole) {
        require(role != ListRole.OWNER) { "Non si invita come proprietario" }
        val invite = ListInvite(
            listId = list.id,
            listName = list.name,
            role = role,
            fromUid = from.uid,
            fromName = from.name,
            toEmail = toEmail.trim().lowercase(),
            createdAt = System.currentTimeMillis(),
        )
        invites.document(invite.id).set(invite.toMap()).logFailure("sendInvite")
    }

    /** Rifiuto (dal destinatario) o ritiro (dal proprietario): in entrambi i casi l'invito sparisce. */
    fun deleteInvite(invite: ListInvite) {
        invites.document(invite.id).delete().logFailure("deleteInvite")
    }

    /**
     * Unica scrittura attesa: chi accetta non ha ancora la lista nella cache locale, quindi senza la
     * conferma del server non saprebbe se l'ingresso è riuscito (lista eliminata, invito ritirato…).
     * Ingresso e cancellazione dell'invito vanno insieme: le regole leggono l'invito con `get()`,
     * che in un batch vede lo stato precedente, quindi l'invito risulta ancora presente.
     */
    suspend fun acceptInvite(invite: ListInvite, me: UserProfile) {
        val batch = db.batch()
        batch.update(
            lists.document(invite.listId),
            FieldPath.of("members", me.uid), invite.role.key,
            "memberIds", FieldValue.arrayUnion(me.uid),
            FieldPath.of("profiles", me.uid), me.toMemberMap(),
            "updatedAt", System.currentTimeMillis(),
        )
        batch.delete(invites.document(invite.id))
        withTimeout(ACCEPT_TIMEOUT_MS) { batch.commit().await() }
    }

    // ---------- Migrazione ----------

    /**
     * Le prime versioni salvavano le liste in users/{uid}/lists, visibili solo al proprietario.
     * Al primo accesso con questa versione le sposta in lists/ con lo stesso ID e cancella le vecchie.
     * Si può togliere quando nessuno ha più liste nel vecchio percorso.
     */
    suspend fun migrateLegacyLists(owner: UserProfile) {
        val legacy = db.collection("users").document(owner.uid).collection("lists").get().await().documents
        if (legacy.isEmpty()) return
        db.batch().apply {
            legacy.forEach { doc ->
                val name = doc.getString("name") ?: return@forEach
                val animeIds = (doc.get("animeIds") as? List<*>).orEmpty().mapNotNull { (it as? Number)?.toInt() }
                val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                set(lists.document(doc.id), newListMap(owner, name, createdAt, animeIds))
                delete(doc.reference)
            }
        }.commit().logFailure("migrateLegacyLists")
    }

    private fun Task<*>.logFailure(op: String) {
        addOnFailureListener { Log.e(TAG, "Scrittura Firestore fallita: $op", it) }
    }

    private companion object {
        const val TAG = "ListDataSource"
        const val ACCEPT_TIMEOUT_MS = 15_000L
    }
}

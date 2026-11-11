# AnimeWatch — specifiche di progetto

App Android nativa per gestire gli anime da vedere: liste personali, progresso puntata per puntata,
anime consigliati in base ai generi già guardati. Dati anime da AniList, dati utente su Firebase.

> Questo file è il contratto del progetto: chi lavora sul codice (persone o Claude) lo legge prima di
> modificare qualcosa e lo aggiorna se cambia una decisione.

## 1. Requisiti funzionali

1. **Liste.** L'utente crea più liste con nome libero (es. "Da vedere in inverno"), vi aggiunge e toglie anime, le elimina.
   Il "+" su un anime apre un pannello dal basso con le liste da spuntare e "Nuova lista", che la crea con l'anime già dentro.
   Dentro una lista, "Aggiungi anime" apre la ricerca: ciò che si aggiunge finisce direttamente in quella lista.
2. **Puntate.** Nella scheda di un anime ogni puntata è una chip che si segna/toglie come vista.
3. **Stagioni.** In AniList ogni stagione è una scheda a sé (S1, S2… sono ID diversi), quindi *stagione* = *scheda anime*.
   Le schede collegate come prequel/sequel (film e OVA compresi) formano un **franchise**: nelle liste è un anime solo,
   nella scheda un menu a tendina (S1, S2, Film…) sceglie la stagione di cui segnare le puntate.
   Una stagione risulta **vista** solo se è conclusa (`status = FINISHED`) **e** tutte le puntate sono segnate.
   Pulsante "Segna tutte come viste" per chiudere la stagione in un tocco; "Azzera" per annullare.
   Per le serie in corso le puntate disponibili sono quelle già uscite (`nextAiringEpisode - 1`).
4. **Progresso unico.** Il progresso è per utente e per anime, **non** per lista: lo stesso anime in due liste ha lo stesso stato.
5. **Cerca.** Ricerca per titolo (romaji/inglese) sul catalogo AniList, con debounce di 500 ms, risultati dal più famoso.
   Le stagioni di una stessa serie trovate insieme diventano una riga sola, con la stagione più famosa. Se un testo di più
   parole non trova nulla si riprova una volta senza l'ultima (AniList confronta parole intere) e lo si dice sopra i risultati.
6. **Consigliati.** Sezione con anime dai generi simili a quelli già guardati (algoritmo in §7), esclusi quelli già in lista o iniziati.
   Riordino opzionale con un LLM (Gemini, piano gratuito), che aggiunge un motivo in italiano per ogni consiglio.
7. **Offline.** Liste e progressi si leggono e si modificano anche senza rete (cache locale di Firestore) e si sincronizzano dopo.
8. **Accesso.** Solo con account Google, da una schermata di login al primo avvio; la sessione resta finché l'utente non tocca "Esci".
9. **Liste condivise.** Il proprietario invita una persona scrivendo l'email del suo account Google e scegliendo
   *modifica* (aggiunge/toglie anime) o *visualizzazione* (sola lettura). L'invito compare negli **Inviti** del Profilo
   del destinatario, che lo accetta o lo rifiuta; non scade, resta finché non viene accettato, rifiutato o ritirato.
   Accettata, la lista sta fra le sue con un contrassegno "Condivisa". Il progresso resta personale (punto 4):
   ognuno vede le proprie puntate. Ruoli e permessi in §5.
10. **Aggiornamenti.** L'app si installa a mano (niente Play Store) e si aggiorna da sola dalle release GitHub (§13):
   all'uscita di una versione mostra una volta il popup "Nuova versione disponibile" con le note di tutte le versioni mancanti; poi resta
   il pulsante "Aggiorna" nel Profilo, dove la pagina Aggiornamenti mostra versione installata e versione nuova.
11. **Profilo.** In alto a destra di ogni schermata, la foto dell'account (un'icona generica se manca o non si carica)
   apre il Profilo: nome ed email, Inviti, Aggiornamenti, "Esci dall'account". Sulla foto, il numero di inviti in attesa
   o un pallino se c'è un aggiornamento.

Fuori scopo per ora: notifiche sulle nuove puntate (anche per gli inviti), streaming/link ai player, social oltre alle
liste condivise (commenti, profili pubblici), tracciamento dei voti personali, trasferimento della proprietà di una lista.

## 2. Stack

| Area | Scelta | Versione |
|---|---|---|
| Linguaggio | Kotlin | 2.0.20 |
| UI | Jetpack Compose + Material 3 (Material You su Android 12+) | BOM 2024.09.03 |
| Navigazione | Navigation Compose, rotte stringa | 2.8.2 |
| Architettura | MVVM, `StateFlow`, un `AppViewModel` condiviso + un ViewModel per Cerca, Consigliati e aggiornamenti | Lifecycle 2.8.6 |
| DI | Manuale (`di/AppContainer.kt`), niente Hilt | — |
| Backend utente | Firebase Auth (solo Google) + Cloud Firestore + Crashlytics | BoM 33.4.0, plugin Crashlytics 3.0.2 |
| Accesso Google | Credential Manager (`androidx.credentials`) + Google ID | 1.3.0 / 1.1.1 |
| Dati anime | AniList GraphQL, `https://graphql.anilist.co/` | pubblico, senza chiave |
| LLM (opzionale) | Gemini API (Google AI Studio, piano gratuito), raggiunta tramite il proxy | `gemini-flash-lite-latest` |
| Proxy AI | Cloudflare Worker (`proxy/`, JavaScript), custodisce la chiave Gemini | Wrangler 4.160.0 |
| Rete | Retrofit + OkHttp + kotlinx.serialization | 2.11.0 / 4.12.0 / 1.7.3 |
| Immagini | Coil | 2.7.0 |
| Build | Gradle 8.9, AGP 8.5.2, version catalog | JDK 17 |
| SDK Android | minSdk 26, targetSdk/compileSdk 34 | — |
| Test | JUnit 4 (unit test JVM sul motore di raccomandazione) | 4.13.2 |

### Perché Kotlin e non Go
Go su Android passa da `gomobile` o Fyne: l'SDK ufficiale di Firebase per Android esiste solo in Kotlin/Java,
e una UI ricca e nativa (Compose, Material You, accessibilità) in Go sarebbe un ripiego. Kotlin è il linguaggio
di prima classe della piattaforma. Se in futuro serve codice Go, il posto giusto è un backend (Cloud Run) dietro l'app.

### Perché AniList
Il "DB condiviso di storie di anime" richiesto **non esiste come database pubblico su Firebase**: Firebase è un
servizio di hosting, non un catalogo. I cataloghi pubblici esistenti sono AniList (GraphQL), Jikan (dati MyAnimeList,
rate limit stretto, non ufficiale) e Kitsu. AniList è stato scelto perché: nessuna chiave, generi e stato della serie
affidabili, numero puntate e prossima puntata in corso, copertine, ricerca per titolo.
Firestore fa da **cache condivisa** davanti ad AniList (§5), così si fa una sola chiamata esterna per anime per tutti gli utenti.

### Perché un proxy su Cloudflare
Una chiave dentro l'APK si estrae sempre: decompilando l'app o leggendo la richiesta. Le release sono pubbliche, quindi
la chiave Gemini sta solo nel Worker, come segreto. Cloudflare e non una Cloud Function perché i Workers sono gratuiti
senza carta, mentre le Functions richiedono il piano Blaze di Firebase.

### Perché Gemini
Gratis senza carta e con una quota **del progetto Google**, non condivisa. Prima c'era OpenRouter: i suoi modelli a
pagamento costano (anche se poco) e quelli `:free` hanno limiti condivisi fra tutti i suoi utenti, quasi sempre saturi
nelle ore di punta; alcuni inoltre ragionano fino a esaurire i token e rispondono vuoto. Gemini accetta uno schema JSON
della risposta e lo rispetta. **Mai modelli a pagamento:** la chiave è su un progetto senza fatturazione, così non può
costare nulla. Sul piano gratuito Google può usare i prompt per addestrare i modelli: contengono solo generi e titoli.

## 3. Struttura del codice

```
app/src/main/java/com/example/animewatch/
├─ AnimeWatchApp.kt            crea AppContainer, passa l'UID a Crashlytics
├─ MainActivity.kt             setContent { Theme { AnimeWatchRoot } }
├─ di/AppContainer.kt          costruisce Retrofit, Firestore, repository, servizi
├─ data/
│  ├─ AppErrors.kt             tipo di un errore (rete, servizio, download corrotto, spazio, inatteso) (puro, testato)
│  ├─ AppLog.kt                log: logcat, righe allegate ai report di Crashlytics, non fatali per gli errori inattesi (§8)
│  ├─ model/Models.kt          Anime, WatchProgress, AnimeList, ListRole, ListInvite, UserProfile (+ regole di dominio)
│  ├─ model/Franchise.kt       stagioni di una serie: raggruppamento (anche di risultati e consigli), ordine, etichette, stagione corrente (puro, testato)
│  ├─ AnimeRepository.kt       memoria -> Firestore `anime/` -> AniList; catena prequel/sequel a ondate
│  ├─ firebase/AuthDataSource  accesso con Google (Credential Manager -> Firebase), uscita, profilo utente
│  ├─ firebase/ListDataSource  liste (anche condivise), membri, inviti, migrazione dal vecchio formato
│  ├─ firebase/UserDataSource  progressi (flussi in tempo reale)
│  ├─ firebase/Mappers.kt      modelli <-> documenti Firestore
│  ├─ remote/
│  │  ├─ anilist/              AniListApi (1 endpoint POST), AniListClient (query), DTO
│  │  └─ ai/                   AiProxyApi (chat/completions e preflight, tramite il proxy)
│  └─ update/                  AppUpdater (release GitHub, download, installer), GitHubApi, AppVersion e PendingReleases (puri, testati)
├─ domain/
│  ├─ RecommendationEngine     profilo generi + coseno (puro, testato)
│  ├─ RecommendationService    candidati da AniList -> ranking -> riordino AI opzionale
│  └─ LlmReranker              prompt e schema della risposta, parsing JSON, fallback
└─ ui/
   ├─ AnimeWatchRoot.kt        login se non c'è utente, altrimenti NavHost + bottom bar + banner errori
   ├─ AppViewModel.kt          utente, liste, inviti, progressi, cache schede, azioni
   ├─ ErrorMessages.kt         testi d'errore per l'utente secondo AppErrors; il dettaglio va ad AppLog
   ├─ components/Common.kt     AnimeRow, CoverImage, UserAvatar, EmptyState, etichette
   ├─ components/Skeleton.kt   skeleton di attesa: forma pulsante, righe di testo, righe di anime
   ├─ components/AddToListSheet.kt  pannello dal basso "Aggiungi a una lista" (anche crea una lista con l'anime dentro)
   ├─ components/ScreenBar.kt  titolo e azioni che ogni schermata dichiara per la barra in alto comune
   ├─ login/  lists/  invites/  detail/  search/  recs/  profile/   una cartella per schermata
   ├─ update/                  UpdateViewModel (quando controllare, popup una volta per versione), UpdateDialog, UpdatesScreen
   └─ theme/Theme.kt

proxy/                          Cloudflare Worker "animewatch-ai" (fuori dall'app, deploy con wrangler)
├─ src/index.js                 POST /chat/completions: token Firebase, limite per utente, modello fisso, traduzione per Gemini;
│                               GET /status: preflight su chiave e modello
├─ src/firebase-auth.js         verifica degli ID token Firebase (firma RS256 con le chiavi di Google, aud/iss/exp/sub)
├─ test/                        node --test sulla verifica del token
└─ wrangler.toml                nome, variabili (progetto Firebase, modello, max token), limite di richieste
```

Dipendenze ammesse: `ui -> domain/data`, `domain -> data`, `data` non conosce `ui`. `RecommendationEngine` non deve
importare nulla di Android (resta testabile sulla JVM).

## 4. Schermate

| Rotta | Contenuto |
|---|---|
| — (login) | Fuori dal NavHost: logo, pulsante "Accedi con Google", errore. Compare finché non c'è un utente |
| — (barra in alto) | Una sola, in `AnimeWatchRoot`: titolo e azioni dalla schermata (`ScreenBar`), freccia indietro fuori dalle sezioni della barra in basso, foto del profilo a destra tranne che nelle pagine `profile…` |
| — (barra in basso) | Liste, Cerca, Consigliati su ogni schermata tranne le pagine `profile…`. È accesa la sezione da cui si è arrivati (l'ultima radice nello stack); ogni sezione conserva il proprio stack, e ritoccare quella attiva riporta alla sua radice |
| `lists` | Skeleton finché non arriva la prima lettura delle liste (il messaggio "Nessuna lista" solo dopo). Elenco liste (n. anime, n. stagioni viste, contrassegno "Condivisa"; al posto del numero di stagioni viste uno skeleton finché le serie della lista non sono tutte ricostruite o fallite), FAB "nuova lista", elimina (proprietario) o esci (altri) con conferma |
| `profile` | Foto, nome, email; "Inviti" (quanti in attesa), "Aggiornamenti" (versione installata, "Aggiorna" se ce n'è una nuova), "Esci dall'account" con conferma |
| `profile/updates` | Versione installata e nuova; "Aggiorna alla versione X" (apre il popup) oppure "Verifica aggiornamenti" |
| — (aggiornamento) | Popup fuori dal NavHost, anche sul login: versione nuova, note della release, avanzamento del download; "Aggiorna ora" / "Più tardi" |
| `list/{listId}` | Una riga per serie (copertina della S1): stagioni viste su totali, puntate e barra della stagione corrente, spunta se tutte viste; il tocco apre la stagione corrente; rimuovi (tutte le stagioni) e pulsante "Aggiungi anime" solo con permesso di modifica; icona condivisione |
| `list/{listId}/members` | Membri con ruolo. Proprietario: invito per email + ruolo, inviti in attesa da ritirare, cambia ruolo / rimuovi membro. Altri: "Esci dalla lista" |
| `list/{listId}/search` | La ricerca di `search` legata alla lista (titolo "Aggiungi a …", tastiera aperta all'arrivo): il "+" aggiunge la serie alla lista e diventa una spunta che la toglie. Se la lista sparisce o non è più modificabile torna la ricerca normale |
| `profile/invites` | Inviti ricevuti: lista, chi invita, ruolo; "Accetta" / "Rifiuta" |
| `anime/{animeId}` | Copertina, generi, trama, "Aggiungi a una lista" (aggiunge la serie), sotto il menu a tendina "Stagione" (se più d'una: etichetta, titolo, spunta sulle viste; c'è da subito se la scheda ha prequel/sequel e, finché le stagioni caricano, mostra il titolo di quella aperta e un indicatore di attesa), griglia di chip per puntata, "Segna tutte"/"Azzera" |
| `search` | Campo di ricerca, skeleton dei risultati durante la ricerca, una riga per serie dalla più popolare (la sua stagione più popolare fra i risultati) con "+" per aggiungere a una lista; sopra, l'avviso se si è cercato senza l'ultima parola |
| — (pannello "Aggiungi a una lista") | Dal "+" di Cerca e Consigliati e dal pulsante della scheda: copertina e titolo, una scheda per lista modificabile da accendere/spegnere (vale per la serie), "Nuova lista con questo anime", "Fatto" |
| `recs` | Solo se il proxy è configurato, un'intestazione di altezza fissa (nulla si sposta quando il pulsante cambia forma) col pulsante "Affina con Gemini" e il suo logo a destra, disattivato con "Gemini non è disponibile al momento" se il preflight dice no. Acceso si riduce a un cerchio col logo, con un anello mentre l'AI lavora e, a riordino fatto, logo verde e spunta animata; ritoccato torna l'elenco dell'algoritmo. Se l'algoritmo fallisce o non trova candidati torna aperto (ritoccarlo riprova). Skeleton al primo calcolo; poi elenco con affinità % e motivo, con la stella di Gemini davanti all'affinità se l'ha data l'AI; mentre si ricalcola resta quello di prima, sbiadito. I generi dominanti non si mostrano |

Titoli degli anime ovunque in inglese, se AniList lo conosce, altrimenti in romaji (`Anime.englishOrRomaji`); nella
scheda il romaji compare sotto come sottotitolo. La ricerca resta su entrambi.

Stringhe UI in italiano scritte direttamente nei composable (app monolingua, scelta volontaria: se serve l'inglese,
spostarle in `strings.xml`).

## 5. Modello dati Firestore

```
anime/{anilistId}                    CACHE CONDIVISA fra tutti gli utenti
  id, title, titleEnglish?, coverUrl?, genres[], episodes?, airedEpisodes?,
  status?, season?, seasonYear?, averageScore?, description?, format?,
  prequelIds[], sequelIds[]          (anime collegati, esclusi i video musicali), cachedAt (ms),
  v                                  versione del formato (ANIME_CACHE_VERSION in Mappers.kt)

lists/{listId}                       una lista, personale o condivisa
  name, ownerId, createdAt (ms), updatedAt (ms), animeIds[]   (ID della prima stagione di ogni serie; arrayUnion/arrayRemove)
  members   { uid: "owner" | "editor" | "viewer" }            i permessi (le regole leggono da qui)
  memberIds [uid…]                                            copia delle chiavi di members, per la query array-contains
  profiles  { uid: { name, photoUrl? } }                      nome e foto da mostrare; ognuno scrive il proprio

invites/{listId}_{email}             invito in attesa verso un account Google
  listId, listName, role ("editor" | "viewer"), fromUid, fromName, toEmail (minuscola), createdAt (ms)

users/{uid}/progress/{anilistId}     un documento per anime con almeno una puntata vista
  animeId, watchedEpisodes[], genres[], totalEpisodes, updatedAt (ms)
```

**Ruoli.**

| | Proprietario | Modifica (`editor`) | Visualizzazione (`viewer`) |
|---|---|---|---|
| Vedere lista e membri | ✓ | ✓ | ✓ |
| Aggiungere/togliere anime | ✓ | ✓ | |
| Eliminare, invitare, cambiare ruoli, rimuovere membri (e rinominare: ammesso dalle regole, manca la UI) | ✓ | | |
| Uscire dalla lista | — | ✓ | ✓ |

**Collegamento dei collaboratori.** Il client non può cercare un utente per email (serve l'Admin SDK), quindi l'invito
è indirizzato all'email e il destinatario lo trova con `invites where toEmail == <sua email>`. L'ID `listId_email` fa sì che
ci sia un solo invito per persona e lista, e permette alle regole di trovarlo senza che il client lo passi.
Accettare = un batch che aggiunge se stessi a `members`/`memberIds`/`profiles` e cancella l'invito; la regola `join`
verifica che esista l'invito a proprio nome per quella lista e che il ruolo scritto sia quello dell'invito.
Il proprietario non può aggiungere membri a mano: si entra solo tramite invito.

**Migrazione.** Le prime versioni salvavano le liste in `users/{uid}/lists`. A ogni accesso `migrateLegacyLists` le sposta
in `lists/` con lo stesso ID e cancella le vecchie; quando non ce ne sono più, il codice si può togliere.

- `genres` è copiato nel progresso (denormalizzazione voluta): il profilo dei gusti si calcola leggendo solo `progress`.
- I campi null **non** si scrivono (le regole validano solo i campi presenti).
- Se non resta nessuna puntata vista il documento di progresso viene **cancellato**.
- TTL della cache: serie concluse 30 giorni (7 se senza seguito, per scoprire una nuova stagione annunciata), serie in corso
  12 ore (escono nuove puntate). Le schede con `v` mancante o minore di `ANIME_CACHE_VERSION` valgono come scadute:
  si incrementa quando cambia ciò che si ricava da AniList, altrimenti le schede vecchie restano fino alla scadenza.
  Se AniList non risponde si usa il dato scaduto.
- **Franchise.** Non si salvano: si ricostruiscono nel client seguendo `prequelIds`/`sequelIds` (`getWithRelated`, una
  `getMany` per livello della catena, al massimo 30 livelli e 300 schede). Ogni livello legge prima la copia di `anime/`
  sul dispositivo (`Source.CACHE`) e va al server solo per le schede mancanti o scadute: con la rete un `get()` normale
  andrebbe sempre al server, un'andata e ritorno per livello. Le serie già complete si pubblicano a ogni livello, senza
  aspettare la catena più lunga (`Franchise.groupComplete`). La lista salva la stagione uscita per prima;
  aggiungere un'altra stagione della stessa serie non cambia nulla, togliere la serie toglie tutte le sue stagioni.
  Le liste di prima possono avere più stagioni della stessa serie: si mostrano come una riga sola, senza migrazione.
  Il progresso resta per stagione (`users/{uid}/progress/{anilistId}`).
- `firestore.rules`: `anime/` leggibile/scrivibile da utenti autenticati con validazione di campi, tipi e ID, **mai cancellabile**;
  `users/{uid}/**` solo dal proprietario; `lists/` leggibile dai membri e modificabile secondo il ruolo (tabella sopra);
  `invites/` leggibile e cancellabile solo da mittente e destinatario, creabile solo dal proprietario della lista.
- Le query sono costruite perché le regole le possano verificare: `lists` con `memberIds array-contains uid`, `invites` con
  `toEmail ==` oppure `fromUid ==` + `listId ==`. Niente `orderBy` (l'ordinamento si fa nel client): eviterebbe indici compositi.
- **Le scritture non si attendono** (`.await()` solo sulle letture). Firestore le applica subito alla cache locale; attenderle
  bloccherebbe la UI finché il server non conferma, cioè all'infinito offline. Gli errori (una scrittura rifiutata dalle
  regole va persa senza che l'utente lo sappia) finiscono nel log e in Crashlytics (`AppLog.error`).
  **Eccezione:** accettare un invito si attende (con timeout di 15 s), perché solo il server può dire se l'invito è ancora valido
  e la lista non è ancora nella cache locale di chi entra.

## 6. API esterne

**AniList** — un solo endpoint, `POST /` con `{query, variables}`. Tre query in `AniListClient`:
`search` (`media(search:…, sort: SEARCH_MATCH)`, i 25 più pertinenti riordinati nel client per `popularity`: con
`POPULARITY_DESC` lato AniList i titoli trovati per sinonimo scavalcano quelli giusti), `byIds` (`id_in`, max 50), `popularByGenre` (`genre:…`, `POPULARITY_DESC`, formati TV/MOVIE/OVA/ONA).
Sempre `isAdult: false` dove si cerca. Limite ~90 richieste/minuto: le chiamate sono ridotte da cache e debounce.
Ogni query chiede anche `format` e `relations` (`relationType(version: 2)`): si tengono solo `PREQUEL`/`SEQUEL` verso
anime di qualsiasi formato tranne i video musicali. Gli speciali restano perché a volte sono l'unico anello fra due
stagioni (Dr. Stone: Stone Wars -> speciale "Ryusui" -> New World), e restano le schede annunciate, che su AniList non
hanno ancora un formato. AniList non
mette sempre la relazione inversa, quindi il raggruppamento la tratta come non orientata.

**Gemini, tramite il proxy** — l'app parla il formato chat/completions di OpenAI: `POST <aiProxyUrl>chat/completions`
con header `Authorization: Bearer <ID token Firebase dell'utente>` e corpo `{messages, temperature, response_format}`,
dove `response_format` è `{type: "json_schema", json_schema: {name, schema}}`. Il Worker:
1. verifica il token (progetto `FIREBASE_PROJECT_ID`) e risponde 401 se non è valido;
2. applica il limite di 10 richieste al minuto per uid (429);
3. accetta al massimo 10 messaggi e 64 KB;
4. traduce per `generateContent` di Gemini: messaggi `system` in `systemInstruction`, modalità JSON, lo schema in
   `responseJsonSchema` (il modello è obbligato a rispettarlo: col solo prompt un id è arrivato come
   `"21827 | Mob Psycho 100"`), ragionamento al minimo (`GEMINI_THINKING`, oggi `low`), `maxOutputTokens`;
5. fissa i modelli (`GEMINI_MODELS`, in ordine di preferenza; oggi l'alias `gemini-flash-lite-latest`, che segue l'ultima
   versione perché Google ritira i modelli e un ID fisso smetterebbe di funzionare, poi `gemini-3.1-flash-lite`): se uno
   risponde 429 (quota gratuita, che è per modello), 500 o 503 (sovraccarico, capita) prova il successivo.
   `gemini-2.5-*` non esiste già più per le chiavi nuove; `gemini-flash-latest` impiegava un minuto.
   Aggiunge la chiave (segreto `GEMINI_API_KEY`);
6. restituisce la risposta nel formato OpenAI (`choices[0].message.content`) e registra modello, motivo di arresto e
   token; gli errori di Gemini tornano con lo stesso codice (429 = quota gratuita finita) e il messaggio.
Il modello riceve una **lista chiusa** di 30 candidati reali (id, titolo, generi, voto medio), i generi dominanti e fino a
200 anime visti (in pratica tutti; il limite tiene la richiesta sotto i 64 KB) dai più recenti, con quanto se ne è visto ("tutto", "12/24"), e restituisce per ognuno affinità 0-100 e
motivo: `[{id, affinity, reason}]` (§7.5). Non può inventare titoli. Ogni anomalia (rete, JSON non valido, ID sconosciuti) => `null` => si usa l'algoritmo.
Risponde in 2-4 secondi; l'app aspetta comunque fino a 90 s (lettura e chiamata).

**Preflight** — `GET <aiProxyUrl>status`, stesso token e stesso limite per uid, risponde `{available}`: il Worker legge i
metadati del primo modello (`GET models/<modello>`, non consuma quota), quindi verifica chiave e modello. La quota
residua Gemini non la espone. L'app lo chiama all'apertura di Consigliati (se l'AI non risulta già disponibile) e su "Aggiorna"
(il ricalcolo ne attende l'esito); con `false`, se il preflight fallisce o se un riordino fallisce dopo un preflight riuscito (es. quota finita),
il pulsante Gemini si disattiva con la scritta rossa "Gemini non è disponibile al momento".

**GitHub** — `GET https://api.github.com/repos/<appReleaseRepo>/releases?per_page=30`, senza token (limite 60 richieste/ora
per IP; l'app ne fa una ogni 6 ore): funziona solo se il repository è **pubblico**. Con un repository privato risponde
404 e l'app crede semplicemente che non ci siano release. Si usa l'elenco e non `releases/latest` perché servono le note
di tutte le versioni saltate; bozze e pre-release si scartano nel client. L'APK si scarica da `browser_download_url`
dell'allegato dell'ultima.

## 7. Algoritmo dei consigliati

1. **Profilo.** Per ogni anime con puntate viste, peso = frazione vista (minimo 0,2); si somma il peso a ognuno dei suoi generi
   e si normalizza dividendo per il massimo. Risultato: vettore `genere -> [0,1]`.
2. **Candidati.** I 3 generi col peso maggiore: una richiesta AniList ciascuno (anime più popolari del genere, 40 per genere).
   Una richiesta per genere perché non è garantito se `genre_in` combini i generi in AND o in OR.
3. **Punteggio.** `0,75 · coseno(generi del candidato, profilo) + 0,25 · voto medio/100`. Il coseno misura quanto i generi
   del candidato puntano nella stessa direzione dei gusti dell'utente e non premia chi ha molti generi né chi ha guardato molto.
   Candidati senza alcun genere in comune scartati. Esclusa ogni stagione delle serie già in lista o iniziate: il loro
   franchise (§5) si ricostruisce in parallelo alle richieste dei candidati, di solito dalla memoria perché lo usano le liste.
   Escluso anche chi ha come prequel/sequel diretto una scheda esclusa.
4. **Una serie, una volta.** Senza richieste in più: i candidati collegati come prequel/sequel, anche attraverso l'ID di
   una stagione che non è fra i candidati, diventano un consiglio solo, al posto e col punteggio del migliore, mostrato
   con la stagione uscita prima. Non si ricostruiscono i franchise dei candidati: costerebbe troppe richieste in serie.
5. **Riordino AI (opzionale).** Le prime 30 serie vanno a Gemini, che assegna a ognuna la **sua** affinità (0-100) con un
   motivo, usando ciò che sa di tono, temi e stile confrontato con gli anime visti. Si mostrano le 12 con l'affinità più
   alta, e l'affinità mostrata è la sua. Non vede l'affinità dell'algoritmo: quando la vedeva la ricopiava (±2 punti) e
   l'ordine non cambiava. Senza proxy configurato o in errore si mostrano le prime 20 dell'algoritmo.
6. **Quando si ricalcola.** I consigli (dell'algoritmo e dell'AI, separatamente) restano in memoria in
   `RecommendationService` finché l'utente non **inizia un anime nuovo**: riaprire la schermata, segnare altre puntate di
   anime già iniziati o accendere e spegnere l'AI non costano richieste. "Aggiorna" forza il ricalcolo. Il calcolo
   dell'algoritmo gira nello scope del servizio: accendendo l'AI a metà caricamento la nuova richiesta lo riusa. Gli anime aggiunti a
   una lista nel frattempo li toglie la schermata. Al riavvio dell'app si ricalcola.

## 8. Configurazione

### Firebase (obbligatorio)
1. Console Firebase -> nuovo progetto -> aggiungi app Android con package `com.example.animewatch`.
2. Authentication -> metodo di accesso -> abilita **Google** (crea il client OAuth web usato dall'app).
3. Registra lo SHA-1 del keystore condiviso (sotto, "Firma degli APK") con `firebase apps:android:sha:create <APP_ID> <SHA1>`
   o dalle impostazioni dell'app in console. Senza, il selettore Google fallisce. È già registrato: è l'unico SHA-1
   dell'app, e non se ne aggiungono altri perché tutti firmano con lo stesso keystore.
4. Scarica `google-services.json` in `app/` **dopo** i passi 2 e 3, altrimenti manca il client OAuth (`oauth_client` vuoto)
   e l'accesso dà "Questa copia dell'app non è configurata per l'accesso con Google" ("client OAuth mancante" nel log).
   È nel `.gitignore`; `app/google-services.json.example` mostra il formato.
5. Firestore Database -> crea in modalità produzione, poi pubblica le regole: `firebase deploy --only firestore:rules`
   (usa `firebase.json` e `firestore.rules`) oppure incollale nella console.
6. Se cambi il package name, aggiornalo in `app/build.gradle.kts` (namespace e applicationId) e nella console Firebase.

### Crashlytics
Attivo in debug e in release, senza configurazione: i crash arrivano in console (Crashlytics) all'avvio successivo
dell'app, con l'UID dell'utente come identificatore. Anche gli errori di accesso con Google, che l'app mostra a schermo
senza crashare, sono registrati come *non fatali* (`AuthDataSource.signInWithGoogle`).

### Log: dove trovarli
L'app scrive i log con `data/AppLog.kt`: `warn` per i problemi previsti, `error` per quelli che potrebbero non esserlo.
Entrambi vanno nel logcat e fra le righe che Crashlytics allega al report successivo; `error` manda anche un *non fatale*
a Crashlytics, tranne quando la causa è la rete o un servizio esterno (`AppErrors`), che sarebbero solo rumore.

| Dove | Cosa c'è | Come si legge | Quanto resta |
|---|---|---|---|
| **Crashlytics** (console Firebase, progetto `anime-watch-873b9`) | Crash; non fatali: accesso con Google, errori inattesi mostrati all'utente, scritture Firestore rifiutate (liste, inviti, progressi, cache `anime/`), letture con permessi negati, risposte AniList illeggibili, migrazione fallita, invito non accettato. Su ogni report l'UID e le ultime righe di `AppLog` (scheda *Log*) | Console, cercando per UID; o i tool `crashlytics_*` dell'MCP Firebase. Arrivano all'avvio successivo dell'app | 90 giorni |
| **Log del proxy** (Cloudflare, Workers Logs: `[observability]` in `proxy/wrangler.toml`) | Per ogni riordino modello, motivo di arresto e token; errori e quota di Gemini (429/503), preflight fallito, token rifiutati (401). Niente uid né prompt | Dashboard Cloudflare -> Workers -> `animewatch-ai` -> Logs; in tempo reale `npm run logs` da `proxy/` | Pochi giorni (piano gratuito) |
| **Logcat** | Tutto quello che scrive `AppLog`, anche gli errori di rete e AniList che non vanno in Crashlytics | Telefono collegato: `adb logcat --pid=$(adb shell pidof com.example.animewatch)` | Finché il buffer del telefono non lo sovrascrive |

Non si salvano statistiche d'uso (niente Analytics). Nei messaggi di `AppLog` niente email o altri dati personali:
Crashlytics li conserva legati all'UID.

### Firma degli APK (obbligatorio)
Tutti gli APK, debug e release, su ogni PC, sono firmati con **lo stesso keystore**, SHA-1 `appSigningSha1` in
`gradle.properties`. Così l'accesso con Google funziona a tutti con un solo SHA-1 su Firebase, e chiunque può fare una
release che si installa sopra le precedenti (Android accetta un aggiornamento solo con la stessa firma). Il keystore sta
**fuori dal repo** e lo passa in privato chi gestisce le release; `keystore.properties` (ignorato da git, modello in
`keystore.properties.example`) indica dove si trova. Gradle (`verifySigningKey`, prima di ogni build) si ferma se il file
manca o se lo SHA-1 è diverso. La CI fa eccezione: compila senza segreti e non distribuisce nulla. La firma **non deve
cambiare mai**. Dà accesso anche a tutto ciò che è firmato con lo stesso keystore: va condiviso solo con chi fa le release.

### Proxy AI (Cloudflare Worker)
L'app non ha bisogno di nessuna chiave. Conosce solo l'indirizzo del Worker, `aiProxyUrl` in `gradle.properties`: se è
vuoto il pulsante Gemini non compare. Il Worker `animewatch-ai` è sull'account Cloudflare di chi lo gestisce; per
modificarlo serve l'accesso a quell'account, che Cloudflare permette di condividere invitando membri. Da `proxy/`:
```
npm install                                  # una volta: wrangler alla versione fissata
npm test                                     # test della verifica del token
npx wrangler login                           # una volta per PC (apre il browser)
npm run deploy                               # pubblica; stampa l'URL da mettere in aiProxyUrl
npx wrangler secret put GEMINI_API_KEY       # la chiave, solo la prima volta o quando cambia
npm run logs                                 # log in tempo reale (401, 429, errori e risposte di Gemini)
```
La chiave si crea su aistudio.google.com/apikey, su un progetto Google Cloud **senza fatturazione** (piano gratuito: non
può costare nulla). Modello, ragionamento, `max_tokens` e limite di richieste stanno in `proxy/wrangler.toml`.
Cambiarli non richiede una nuova versione dell'app, basta un deploy. I modelli disponibili per la chiave:
`GET https://generativelanguage.googleapis.com/v1beta/models` con header `x-goog-api-key`.

### Build e test
```
./gradlew :app:testDebugUnitTest          # unit test (raccomandazioni, liste condivise, franchise, versioni, errori)
./gradlew :app:assembleDebug              # APK in app/build/outputs/apk/debug/
.\install.ps1 [-Release] [-Serial <id>]   # git fetch + pull --ff-only, build, adb install -r sul telefono collegato
```
`install.ps1` controlla git, adb e telefono prima di iniziare e si ferma al primo errore indicando passo e motivo
(anche con Ctrl+C); il pull è solo fast-forward, quindi con branch divergenti non fa merge.
`.\pair-wifi.ps1` collega il telefono via Wi-Fi: se è attaccato al cavo accende da solo il debug wireless, poi trova
indirizzo e porta via mDNS (la porta cambia a ogni accensione) e fa `adb connect`. Se il PC non è abbinato (la prima
volta per ogni coppia PC-telefono) fa anche `adb pair`: si apre "Abbina dispositivo con codice" sul telefono e si
scrive solo il codice, indirizzo e porta li trova da solo.
Android Studio (Koala o successivo) apre la cartella e fa tutto da solo. La CI (`.github/workflows/build.yml`) usa il
`google-services.json.example` come segnaposto, quindi compila senza segreti.

## 9. Sicurezza e limiti noti

- **Proxy AI.** La chiave Gemini non è nell'APK. L'URL del Worker è pubblico, ma risponde solo a chi ha un ID token
  del progetto Firebase. Chiunque abbia un account Google può però accedere all'app e quindi usare il proxy, entro
  10 richieste al minuto. La chiave è sul piano gratuito, senza fatturazione: non si paga nulla. La quota giornaliera
  è condivisa da tutti gli utenti dell'app; finita, il pulsante Gemini risulta "non disponibile al momento" fino al
  giorno dopo. Google cambia ogni tanto quote e modelli gratuiti.
- **Aggiornamenti.** Il repository delle release deve essere pubblico (§6). L'APK scaricato è verificato con dimensione e
  sha256 di GitHub; Android stesso rifiuta un APK con firma diversa o `versionCode` non maggiore.
- **Cache `anime/` scrivibile dai client.** Le regole limitano forma e tipi ma un utente malevolo potrebbe scrivere dati falsi
  su un ID. Mitigazione futura: Firebase App Check, oppure scrittura solo da Cloud Function.
- **Solo accesso con Google.** L'UID è legato all'account Google, quindi liste e progressi seguono l'utente su ogni
  dispositivo e sopravvivono alla disinstallazione. Niente accesso anonimo né email/password.
- **Primo accesso offline.** Il primo accesso richiede rete; dopo, Firebase conserva la sessione e l'app si apre anche offline.
- **Stagioni nei consigli.** Due stagioni della stessa serie compaiono entrambe solo se fra loro mancano, fra i candidati,
  almeno due stagioni consecutive; in quel caso si mostra la prima uscita fra i candidati, che può non essere la S1.
  Se AniList non risponde mentre si ricostruiscono le serie dell'utente, un loro seguito può passare.
- **Stagioni nella ricerca.** Stesso raggruppamento senza richieste in più (`Franchise.collapse`), aiutato dai franchise già
  ricostruiti per le liste: le serie dell'utente si uniscono sempre, le altre solo se fra i risultati non mancano due
  stagioni consecutive. La riga mostra la stagione più famosa, non la prima uscita: su AniList Code Geass Re;surrection
  è il seguito dei tre film riassuntivi, e mostrando il primo film il titolo cercato non si vedeva. Cercando "… season 2"
  la riga mostra di solito la S1; il selettore della scheda porta alla S2.
- **Ricerca per parole intere.** AniList non trova "resurrection" in "Re;surrection" né le parole a metà: il secondo
  tentativo senza l'ultima parola copre i casi in cui la parola diversa è l'ultima, non gli altri.
- **Confini del franchise.** Dipendono dalle relazioni di AniList: uno spin-off (`SPIN_OFF`, `SIDE_STORY`) resta una serie a
  sé. Uno speciale-riassunto collegato come sequel compare fra le stagioni ("Speciale"). L'ordine delle stagioni è per
  anno e stagione d'uscita.
- **Inviti per email esatta.** L'invito vale per l'email scritta, confrontata in minuscolo con quella dell'account Google.
  Alias come `nome.cognome@gmail.com` / `nomecognome@gmail.com` o `+etichetta` non vengono riconosciuti: serve l'email
  con cui la persona accede. Non c'è notifica: il destinatario vede l'invito aprendo l'app.
- **Nomi e foto nelle liste condivise.** Sono copiati in `profiles` al momento della creazione o dell'ingresso e non si
  aggiornano se l'utente cambia nome sull'account Google.
- **Titoli adulti.** Esclusi con `isAdult: false` lato AniList.
- **Pubblicazione.** `isMinifyEnabled = false` nella release: se si attiva R8 servono regole per kotlinx.serialization e Retrofit.

## 10. Stato della verifica

Verificato (10/11/2026):
- `assembleDebug` compila con JDK 17; l'APK si installa e parte su dispositivo (Android 16).
- `testDebugUnitTest` passa: motore di raccomandazione e regole di dominio delle liste condivise (`SharedListTest`).
- Le tre query AniList rispondono senza errori contro il server reale. La ricerca di AniList confronta **parole intere**:
  "fri" non trova nulla, "frieren" sì.
- Accesso con Google funzionante dopo aver registrato lo SHA-1 di debug.

Verificato (11/11/2026):
- `assembleRelease` firma con il keystore condiviso (SHA-1 controllato con apksigner); `versionCode`/`versionName` arrivano da `appVersion`.
- `verifySigningKey` ferma la build con un keystore diverso o senza `keystore.properties`.
- Proxy pubblicato: risponde 401 senza token o con token falsi o firmati da chiavi sconosciute, 405 ai GET e 404 agli
  altri percorsi. `npm test` copre la verifica del token (firma, aud, iss, scadenza, uid).
- "Affina con Gemini" su dispositivo (debug): preflight, riordino con schema e motivi in italiano; con il proxy
  che risponde `false` o senza rete il pulsante è disattivato con "Gemini non è disponibile al momento".
  Cerchio con anello, logo verde e spunta; spento e riacceso torna il risultato in memoria senza chiamare Gemini;
  acceso durante il primo caricamento l'algoritmo si calcola una volta sola; con AniList in errore (simulato) torna aperto.
- Messaggi d'errore su dispositivo: Consigliati e Cerca senza rete, AniList in errore (simulato); tornata la rete, Cerca e
  "Aggiorna" ripartono. Un `AppLog.error` di prova arriva in Crashlytics come non fatale con UID e righe di log; gli errori
  di rete restano fuori.

Da verificare:
1. Su dispositivo, con due account Google: invito, accettazione, rifiuto, sola visualizzazione, uscita, modalità aereo.
2. Aggiornamento completo (popup, download, permesso "installa app sconosciute", installazione): possibile solo con il
   repository pubblico e almeno due release.
3. Franchise su dispositivo, dopo aver pubblicato le regole con i campi nuovi di `anime/` (senza, la scrittura in cache
   fallisce e ogni apertura rilegge da AniList): selettore di stagione, lista con S1 e S2 già presenti, aggiunta della S2
   di una serie già in lista, rimozione, offline.

Le regole di `lists/` e `invites/` sono provate nell'emulatore Firestore con `@firebase/rules-unit-testing`
(66 casi: creazione, lettura, inviti, ingresso con ruolo giusto e sbagliato, editor, viewer, rifiuto, ritiro,
poteri e limiti del proprietario, eliminazione, migrazione). Il test non è nel repo; se si aggiunge, va in `test/rules/`.

## 11. Convenzioni

- Kotlin ufficiale (`kotlin.code.style=official`), 4 spazi, righe ragionevoli (~120).
- Nessuna logica di business nei composable: azioni nei ViewModel, regole di dominio in `data/model` o `domain`.
- Un contenuto che sta arrivando si aspetta con uno skeleton (`ui/components/Skeleton.kt`) delle sue misure, così la pagina
  non si sposta; gli indicatori rotondi e le barre restano per le azioni e gli avanzamenti (pulsanti, Gemini, download).
- Gli stati sono `StateFlow` immutabili; le schermate usano `collectAsStateWithLifecycle()`.
- Mai mostrare all'utente il messaggio di un'eccezione (tecnico e in inglese): i testi d'errore vengono da
  `ui/ErrorMessages.kt`, che dice cosa è successo e cosa fare e lascia il dettaglio a log e Crashlytics.
- Log con `AppLog`, non con `android.util.Log`: altrimenti non arrivano a Crashlytics (§8, "Log: dove trovarli").
  Il codice puro (`RecommendationEngine`, `Franchise`, `AppErrors`…) non logga.
- Ogni nuova query AniList passa da `AniListClient` e restituisce `Anime`; ogni nuovo campo di `Anime` va aggiunto a DTO, mapper Firestore e **regole Firestore** (`hasOnly`).
- I commenti spiegano il *perché* di una scelta non ovvia, non ripetono il codice.
- Mai committare `google-services.json`, `local.properties`, keystore.

## 12. Git

- Si lavora su `dev`; `main` è il branch di rilascio. Le release partono da `main` dopo un merge di `dev`.
- Messaggi di commit: una sola riga, Conventional Commits con gitmoji, in inglese, all'imperativo e in minuscolo, senza scope, body o trailer (niente `Co-Authored-By`). Brevi e diretti: cosa cambia, non perché né come. Esempi: `feat: ✨ add deploy for tagged releases`, `fix: 🐛 display sign-in errors`, `feat: ✨ use checkboxes for dish markers`.
- Le modifiche a questo file (`CLAUDE.md`) vanno nel commit che implementa ciò che descrivono: niente commit `docs` separati, niente fixup del commit che le ha introdotte.
- Commit, push e riscritture della history solo su richiesta.
- Il commit di release (`chore(release): 🚀 release X.Y.Z`, unica eccezione alla regola senza scope, uguale agli altri
  progetti) lo crea solo `release.ps1` (§13), mai a mano.

## 13. Release e aggiornamenti

- **Versione:** unica fonte `appVersion` in `gradle.properties`. `versionName` è `appVersion`, `versionCode` è
  `major·10000 + minor·100 + patch` (minor e patch sotto 100). La incrementa solo lo script.
- **Pubblicare** (Windows, da `main` dopo il merge di `dev`): `.\release.cmd patch|minor|major`. Lo script lo lancia chi
  fa la release, non Claude. Richiede `keystore.properties`, il vero `app/google-services.json` e GitHub CLI
  autenticata con permesso di push (`gh auth login`).
  1. Controlla branch pulito e allineato a origin, `dev` già unito, tag libero e `:app:verifyReleaseConfig`.
     Poi apre il Blocco note per le note di rilascio, che compaiono nel popup dell'app.
     **Le note sono per chi usa l'app**, anche quando le scrive Claude: cosa cambia per l'utente, in italiano, testo
     semplice con trattini (il popup non interpreta il Markdown). Niente dettagli di sviluppo (script, test, CI, regole
     Firestore, refactoring, nomi di file o classi) né indicazioni per lo sviluppatore.
  2. Incrementa `appVersion`.
  3. Lancia gli unit test e `assembleRelease`, copia in `build\release\AnimeWatch.apk` e controlla la firma con apksigner.
  4. Dopo conferma: commit, tag annotato `vX.Y.Z` e push atomico di `main` e tag.
  5. Con `gh release create` crea su `appReleaseRepo` una release (non pre-release) con l'allegato `AnimeWatch.apk`,
     **nome esatto**.
  6. Porta `dev` (locale e origin) al commit di release, se è un avanzamento semplice.
  Se fallisce prima del commit, rimette `gradle.properties` e `build\release` com'erano.
- **Nell'app** (`AppUpdater` + `UpdateViewModel`): il controllo avviene all'avvio e a ogni ritorno nell'app, al massimo
  ogni 6 ore dopo un controllo riuscito o ogni 2 minuti dopo uno fallito. Si confrontano i tag delle release con
  `BuildConfig.VERSION_NAME` (`PendingReleases`, puro e testato): si propone l'ultima, se allega l'APK, e il popup mostra
  le note di **tutte** le versioni successive a quella installata, dalla più recente (al massimo le ultime 30 release).
  Il popup compare una volta per versione (`seenVersion` nelle SharedPreferences "update").
  L'APK si scarica in `files/update/` (file `.part`, verificato, poi rinominato) e si apre l'installer via FileProvider
  (`res/xml/update_paths.xml`). La prima volta Android chiede il permesso "installa app sconosciute" e l'installer parte
  al ritorno nell'app. Il vecchio APK si cancella all'avvio successivo.

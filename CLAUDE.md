# AnimeWatch — specifiche di progetto

App Android nativa per gestire gli anime da vedere: liste personali, progresso puntata per puntata,
anime consigliati in base ai generi già guardati. Dati anime da AniList, dati utente su Firebase.

> Questo file è il contratto del progetto: chi lavora sul codice (persone o Claude) lo legge prima di
> modificare qualcosa e lo aggiorna se cambia una decisione.

## 1. Requisiti funzionali

1. **Liste.** L'utente crea più liste con nome libero (es. "Da vedere in inverno"), vi aggiunge e toglie anime, le elimina.
2. **Puntate.** Nella scheda di un anime ogni puntata è una chip che si segna/toglie come vista.
3. **Stagioni.** In AniList ogni stagione è una scheda a sé (S1, S2… sono ID diversi), quindi *stagione* = *scheda anime*.
   Una stagione risulta **vista** solo se è conclusa (`status = FINISHED`) **e** tutte le puntate sono segnate.
   Pulsante "Segna tutte come viste" per chiudere la stagione in un tocco; "Azzera" per annullare.
   Per le serie in corso le puntate disponibili sono quelle già uscite (`nextAiringEpisode - 1`).
4. **Progresso unico.** Il progresso è per utente e per anime, **non** per lista: lo stesso anime in due liste ha lo stesso stato.
5. **Cerca.** Ricerca per titolo (romaji/inglese) sul catalogo AniList, con debounce di 500 ms.
6. **Consigliati.** Sezione con anime dai generi simili a quelli già guardati (algoritmo in §7), esclusi quelli già in lista o iniziati.
   Riordino opzionale con un LLM via OpenRouter, che aggiunge un motivo in italiano per ogni consiglio.
7. **Offline.** Liste e progressi si leggono e si modificano anche senza rete (cache locale di Firestore) e si sincronizzano dopo.
8. **Accesso.** Solo con account Google, da una schermata di login al primo avvio; la sessione resta finché l'utente non tocca "Esci".
9. **Liste condivise.** Il proprietario invita una persona scrivendo l'email del suo account Google e scegliendo
   *modifica* (aggiunge/toglie anime) o *visualizzazione* (sola lettura). L'invito compare nella sezione **Inviti**
   del destinatario, che lo accetta o lo rifiuta; non scade, resta finché non viene accettato, rifiutato o ritirato.
   Accettata, la lista sta fra le sue con un contrassegno "Condivisa". Il progresso resta personale (punto 4):
   ognuno vede le proprie puntate. Ruoli e permessi in §5.
10. **Aggiornamenti.** L'app si installa a mano (niente Play Store) e si aggiorna da sola dalle release GitHub (§13):
   all'uscita di una versione mostra una volta il popup "Nuova versione disponibile" con le note; poi resta
   "Aggiorna alla versione X" nel menu della schermata Liste, che mostra anche la versione installata.

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
| LLM (opzionale) | OpenRouter, raggiunto tramite il proxy | — |
| Proxy AI | Cloudflare Worker (`proxy/`, JavaScript), custodisce la chiave OpenRouter | Wrangler 4.160.0 |
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
la chiave OpenRouter sta solo nel Worker, come segreto. Cloudflare e non una Cloud Function perché i Workers sono gratuiti
senza carta, mentre le Functions richiedono il piano Blaze di Firebase.

## 3. Struttura del codice

```
app/src/main/java/com/example/animewatch/
├─ AnimeWatchApp.kt            crea AppContainer, passa l'UID a Crashlytics
├─ MainActivity.kt             setContent { Theme { AnimeWatchRoot } }
├─ di/AppContainer.kt          costruisce Retrofit, Firestore, repository, servizi
├─ data/
│  ├─ model/Models.kt          Anime, WatchProgress, AnimeList, ListRole, ListInvite, UserProfile (+ regole di dominio)
│  ├─ AnimeRepository.kt       memoria -> Firestore `anime/` -> AniList
│  ├─ firebase/AuthDataSource  accesso con Google (Credential Manager -> Firebase), uscita, profilo utente
│  ├─ firebase/ListDataSource  liste (anche condivise), membri, inviti, migrazione dal vecchio formato
│  ├─ firebase/UserDataSource  progressi (flussi in tempo reale)
│  ├─ firebase/Mappers.kt      modelli <-> documenti Firestore
│  ├─ remote/
│  │  ├─ anilist/              AniListApi (1 endpoint POST), AniListClient (query), DTO
│  │  └─ openrouter/           OpenRouterApi (chat/completions, tramite il proxy)
│  └─ update/                  AppUpdater (release GitHub, download, installer), GitHubApi, AppVersion (puro, testato)
├─ domain/
│  ├─ RecommendationEngine     profilo generi + coseno (puro, testato)
│  ├─ RecommendationService    candidati da AniList -> ranking -> riordino AI opzionale
│  └─ LlmReranker              prompt a OpenRouter, parsing JSON, fallback
└─ ui/
   ├─ AnimeWatchRoot.kt        login se non c'è utente, altrimenti NavHost + bottom bar + banner errori
   ├─ AppViewModel.kt          utente, liste, inviti, progressi, cache schede, azioni
   ├─ components/Common.kt     AnimeRow, CoverImage, MemberAvatar, AddToListDialog, EmptyState, etichette
   ├─ login/  lists/  invites/  detail/  search/  recs/   una cartella per schermata
   ├─ update/                  UpdateViewModel (quando controllare, popup una volta per versione), UpdateDialog
   └─ theme/Theme.kt

proxy/                          Cloudflare Worker "animewatch-ai" (fuori dall'app, deploy con wrangler)
├─ src/index.js                 POST /chat/completions: token Firebase, limite per utente, modello fisso, inoltro a OpenRouter
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
| `lists` | Elenco liste (n. anime, n. stagioni viste, contrassegno "Condivisa"), FAB "nuova lista", elimina (proprietario) o esci (altri) con conferma; nella barra "Inviti" con contatore e un menu (pallino se c'è un aggiornamento): "Verifica aggiornamenti" / "Aggiorna alla versione X", "Esci dall'account", versione installata |
| — (aggiornamento) | Popup fuori dal NavHost, anche sul login: versione nuova, note della release, avanzamento del download; "Aggiorna ora" / "Più tardi" |
| `list/{listId}` | Anime della lista con barra di avanzamento e spunta se stagione vista; rimuovi dalla lista solo con permesso di modifica; icona condivisione |
| `list/{listId}/members` | Membri con ruolo. Proprietario: invito per email + ruolo, inviti in attesa da ritirare, cambia ruolo / rimuovi membro. Altri: "Esci dalla lista" |
| `invites` | Inviti ricevuti: lista, chi invita, ruolo; "Accetta" / "Rifiuta" |
| `anime/{animeId}` | Copertina, generi, trama, griglia di chip per puntata, "Segna tutte"/"Azzera", "Aggiungi a una lista" |
| `search` | Campo di ricerca, risultati con "+" per aggiungere a una lista |
| `recs` | Generi dominanti, interruttore "Affina con AI" (solo se la chiave c'è), elenco con affinità % e motivo |

Stringhe UI in italiano scritte direttamente nei composable (app monolingua, scelta volontaria: se serve l'inglese,
spostarle in `strings.xml`).

## 5. Modello dati Firestore

```
anime/{anilistId}                    CACHE CONDIVISA fra tutti gli utenti
  id, title, titleEnglish?, coverUrl?, genres[], episodes?, airedEpisodes?,
  status?, season?, seasonYear?, averageScore?, description?, cachedAt (ms)

lists/{listId}                       una lista, personale o condivisa
  name, ownerId, createdAt (ms), updatedAt (ms), animeIds[]   (arrayUnion/arrayRemove: niente duplicati)
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
- TTL della cache: serie concluse 30 giorni, serie in corso 12 ore (escono nuove puntate). Se AniList non risponde si usa il dato scaduto.
- `firestore.rules`: `anime/` leggibile/scrivibile da utenti autenticati con validazione di campi, tipi e ID, **mai cancellabile**;
  `users/{uid}/**` solo dal proprietario; `lists/` leggibile dai membri e modificabile secondo il ruolo (tabella sopra);
  `invites/` leggibile e cancellabile solo da mittente e destinatario, creabile solo dal proprietario della lista.
- Le query sono costruite perché le regole le possano verificare: `lists` con `memberIds array-contains uid`, `invites` con
  `toEmail ==` oppure `fromUid ==` + `listId ==`. Niente `orderBy` (l'ordinamento si fa nel client): eviterebbe indici compositi.
- **Le scritture non si attendono** (`.await()` solo sulle letture). Firestore le applica subito alla cache locale; attenderle
  bloccherebbe la UI finché il server non conferma, cioè all'infinito offline. Gli errori finiscono nel log.
  **Eccezione:** accettare un invito si attende (con timeout di 15 s), perché solo il server può dire se l'invito è ancora valido
  e la lista non è ancora nella cache locale di chi entra.

## 6. API esterne

**AniList** — un solo endpoint, `POST /` con `{query, variables}`. Tre query in `AniListClient`:
`search` (`media(search:…, sort: SEARCH_MATCH)`), `byIds` (`id_in`, max 50), `popularByGenre` (`genre:…`, `POPULARITY_DESC`, formati TV/MOVIE/OVA/ONA).
Sempre `isAdult: false` dove si cerca. Limite ~90 richieste/minuto: le chiamate sono ridotte da cache e debounce.

**OpenRouter, tramite il proxy** — l'app fa `POST <aiProxyUrl>chat/completions` con header
`Authorization: Bearer <ID token Firebase dell'utente>` e corpo `{messages, temperature}`. Il Worker:
1. verifica il token (progetto `FIREBASE_PROJECT_ID`) e risponde 401 se non è valido;
2. applica il limite di 10 richieste al minuto per uid (429);
3. accetta al massimo 10 messaggi e 64 KB;
4. fissa `model` (`OPENROUTER_MODEL`, oggi `openai/gpt-4o-mini`: gli ID cambiano nel tempo, verificarli su
   openrouter.ai/models) e `max_tokens`;
5. aggiunge la chiave (segreto `OPENROUTER_API_KEY`) e restituisce la risposta di OpenRouter così com'è.
Il modello riceve una **lista chiusa** di 30 candidati reali e deve restituire un array JSON `[{id, reason}]`:
non può inventare titoli. Ogni anomalia (rete, JSON non valido, ID sconosciuti) => `null` => si usa l'algoritmo.

**GitHub** — `GET https://api.github.com/repos/<appReleaseRepo>/releases/latest`, senza token (limite 60 richieste/ora
per IP; l'app ne fa una ogni 6 ore): funziona solo se il repository è **pubblico**. Con un repository privato risponde
404 e l'app crede semplicemente che non ci siano release. L'APK si scarica da `browser_download_url` dell'allegato.

## 7. Algoritmo dei consigliati

1. **Profilo.** Per ogni anime con puntate viste, peso = frazione vista (minimo 0,2); si somma il peso a ognuno dei suoi generi
   e si normalizza dividendo per il massimo. Risultato: vettore `genere -> [0,1]`.
2. **Candidati.** I 3 generi col peso maggiore: una richiesta AniList ciascuno (anime più popolari del genere, 40 per genere).
   Una richiesta per genere perché non è garantito se `genre_in` combini i generi in AND o in OR.
3. **Punteggio.** `0,75 · coseno(generi del candidato, profilo) + 0,25 · voto medio/100`. Il coseno misura quanto i generi
   del candidato puntano nella stessa direzione dei gusti dell'utente e non premia chi ha molti generi né chi ha guardato molto.
   Candidati senza alcun genere in comune scartati; già in lista o con progresso esclusi.
4. **Riordino AI (opzionale).** I primi 30 vanno a OpenRouter, che ne sceglie 12 con motivazione; senza proxy configurato o in errore si mostrano i primi 20 dell'algoritmo.
5. **Quando si ricalcola.** All'apertura, al tocco su "Aggiorna", e solo se cambiano i 3 generi dominanti o l'interruttore AI
   (non a ogni puntata segnata: ogni calcolo costa richieste ad AniList).

## 8. Configurazione

### Firebase (obbligatorio)
1. Console Firebase -> nuovo progetto -> aggiungi app Android con package `com.example.animewatch`.
2. Authentication -> metodo di accesso -> abilita **Google** (crea il client OAuth web usato dall'app).
3. Registra lo SHA-1 del keystore condiviso (sotto, "Firma degli APK") con `firebase apps:android:sha:create <APP_ID> <SHA1>`
   o dalle impostazioni dell'app in console. Senza, il selettore Google fallisce. È già registrato: è l'unico SHA-1
   dell'app, e non se ne aggiungono altri perché tutti firmano con lo stesso keystore.
4. Scarica `google-services.json` in `app/` **dopo** i passi 2 e 3, altrimenti manca il client OAuth (`oauth_client` vuoto)
   e l'accesso dà "client OAuth mancante". È nel `.gitignore`; `app/google-services.json.example` mostra il formato.
5. Firestore Database -> crea in modalità produzione, poi pubblica le regole: `firebase deploy --only firestore:rules`
   (usa `firebase.json` e `firestore.rules`) oppure incollale nella console.
6. Se cambi il package name, aggiornalo in `app/build.gradle.kts` (namespace e applicationId) e nella console Firebase.

### Crashlytics
Attivo in debug e in release, senza configurazione: i crash arrivano in console (Crashlytics) all'avvio successivo
dell'app, con l'UID dell'utente come identificatore. Anche gli errori di accesso con Google, che l'app mostra a schermo
senza crashare, sono registrati come *non fatali* (`AuthDataSource.signInWithGoogle`).

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
vuoto l'interruttore AI non compare. Il Worker `animewatch-ai` è sull'account Cloudflare di chi lo gestisce; per
modificarlo serve l'accesso a quell'account, che Cloudflare permette di condividere invitando membri. Da `proxy/`:
```
npm install                                  # una volta: wrangler alla versione fissata
npm test                                     # test della verifica del token
npx wrangler login                           # una volta per PC (apre il browser)
npm run deploy                               # pubblica; stampa l'URL da mettere in aiProxyUrl
npx wrangler secret put OPENROUTER_API_KEY   # la chiave, solo la prima volta o quando cambia
npm run logs                                 # log in tempo reale (401, 429, errori di OpenRouter)
```
Modello, `max_tokens` e limite di richieste stanno in `proxy/wrangler.toml`. Cambiarli non richiede una nuova
versione dell'app, basta un deploy.

### Build e test
```
./gradlew :app:testDebugUnitTest          # unit test (raccomandazioni, liste condivise, versioni)
./gradlew :app:assembleDebug              # APK in app/build/outputs/apk/debug/
.\install.ps1 [-Release] [-Serial <id>]   # git fetch + pull --ff-only, build, adb install -r sul telefono collegato
```
`install.ps1` controlla git, adb e telefono prima di iniziare e si ferma al primo errore indicando passo e motivo
(anche con Ctrl+C); il pull è solo fast-forward, quindi con branch divergenti non fa merge.
Android Studio (Koala o successivo) apre la cartella e fa tutto da solo. La CI (`.github/workflows/build.yml`) usa il
`google-services.json.example` come segnaposto, quindi compila senza segreti.

## 9. Sicurezza e limiti noti

- **Proxy AI.** La chiave OpenRouter non è nell'APK. L'URL del Worker è pubblico, ma risponde solo a chi ha un ID token
  del progetto Firebase. Chiunque abbia un account Google può però accedere all'app e quindi usare il proxy, entro
  10 richieste al minuto. Il tetto vero alla spesa resta il **limite di credito sulla chiave** in OpenRouter.
- **Aggiornamenti.** Il repository delle release deve essere pubblico (§6). L'APK scaricato è verificato con dimensione e
  sha256 di GitHub; Android stesso rifiuta un APK con firma diversa o `versionCode` non maggiore.
- **Cache `anime/` scrivibile dai client.** Le regole limitano forma e tipi ma un utente malevolo potrebbe scrivere dati falsi
  su un ID. Mitigazione futura: Firebase App Check, oppure scrittura solo da Cloud Function.
- **Solo accesso con Google.** L'UID è legato all'account Google, quindi liste e progressi seguono l'utente su ogni
  dispositivo e sopravvivono alla disinstallazione. Niente accesso anonimo né email/password.
- **Primo accesso offline.** Il primo accesso richiede rete; dopo, Firebase conserva la sessione e l'app si apre anche offline.
- **Sequel nei consigli.** Se l'utente guarda S1, la S2 (stessi generi) può comparire tra i consigliati. Soluzione prevista:
  leggere `relations` da AniList ed escludere le schede collegate a ciò che è già visto.
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
  altri percorsi. `npm test` copre la verifica del token (firma, aud, iss, scadenza, uid). L'APK non contiene `sk-or-`.

Da verificare:
1. Su dispositivo, con due account Google: invito, accettazione, rifiuto, sola visualizzazione, uscita, modalità aereo.
2. Aggiornamento completo (popup, download, permesso "installa app sconosciute", installazione): possibile solo con il
   repository pubblico e almeno due release.
3. "Affina con AI" su dispositivo, con un utente vero che passa dal proxy fino a OpenRouter.

Le regole di `lists/` e `invites/` sono provate nell'emulatore Firestore con `@firebase/rules-unit-testing`
(66 casi: creazione, lettura, inviti, ingresso con ruolo giusto e sbagliato, editor, viewer, rifiuto, ritiro,
poteri e limiti del proprietario, eliminazione, migrazione). Il test non è nel repo; se si aggiunge, va in `test/rules/`.

## 11. Convenzioni

- Kotlin ufficiale (`kotlin.code.style=official`), 4 spazi, righe ragionevoli (~120).
- Nessuna logica di business nei composable: azioni nei ViewModel, regole di dominio in `data/model` o `domain`.
- Gli stati sono `StateFlow` immutabili; le schermate usano `collectAsStateWithLifecycle()`.
- Ogni nuova query AniList passa da `AniListClient` e restituisce `Anime`; ogni nuovo campo di `Anime` va aggiunto a DTO, mapper Firestore e **regole Firestore** (`hasOnly`).
- I commenti spiegano il *perché* di una scelta non ovvia, non ripetono il codice.
- Mai committare `google-services.json`, `local.properties`, keystore.

## 12. Git

- Si lavora su `dev`; `main` è il branch di rilascio. Le release partono da `main` dopo un merge di `dev`.
- Messaggi di commit: una sola riga, Conventional Commits con gitmoji, in inglese, all'imperativo e in minuscolo, senza scope, body o trailer (niente `Co-Authored-By`). Brevi e diretti: cosa cambia, non perché né come. Esempi: `feat: ✨ add deploy for tagged releases`, `fix: 🐛 display sign-in errors`, `feat: ✨ use checkboxes for dish markers`.
- Le modifiche a questo file (`CLAUDE.md`) vanno nel commit che implementa ciò che descrivono: niente commit `docs` separati, niente fixup del commit che le ha introdotte.
- Commit, push e riscritture della history solo su richiesta.
- Il commit di release (`chore: 🔖 release X.Y.Z`) lo crea solo `release.ps1` (§13), mai a mano.

## 13. Release e aggiornamenti

- **Versione:** unica fonte `appVersion` in `gradle.properties`. `versionName` è `appVersion`, `versionCode` è
  `major·10000 + minor·100 + patch` (minor e patch sotto 100). La incrementa solo lo script.
- **Pubblicare** (Windows, da `main` dopo il merge di `dev`): `.\release.cmd patch|minor|major`. Lo script lo lancia chi
  fa la release, non Claude. Richiede `keystore.properties`, il vero `app/google-services.json` e GitHub CLI
  autenticata con permesso di push (`gh auth login`).
  1. Controlla branch pulito e allineato a origin, `dev` già unito, tag libero e `:app:verifyReleaseConfig`.
     Poi apre il Blocco note per le note di rilascio, che compaiono nel popup dell'app.
  2. Incrementa `appVersion`.
  3. Lancia gli unit test e `assembleRelease`, copia in `build\release\AnimeWatch.apk` e controlla la firma con apksigner.
  4. Dopo conferma: commit, tag annotato `vX.Y.Z` e push atomico di `main` e tag.
  5. Con `gh release create` crea su `appReleaseRepo` una release (non pre-release) con l'allegato `AnimeWatch.apk`,
     **nome esatto**.
  6. Porta `dev` (locale e origin) al commit di release, se è un avanzamento semplice.
  Se fallisce prima del commit, rimette `gradle.properties` e `build\release` com'erano.
- **Nell'app** (`AppUpdater` + `UpdateViewModel`): il controllo avviene all'avvio e a ogni ritorno nell'app, al massimo
  ogni 6 ore dopo un controllo riuscito o ogni 2 minuti dopo uno fallito. Si confronta il tag dell'ultima release con
  `BuildConfig.VERSION_NAME`. Il popup compare una volta per versione (`seenVersion` nelle SharedPreferences "update").
  L'APK si scarica in `files/update/` (file `.part`, verificato, poi rinominato) e si apre l'installer via FileProvider
  (`res/xml/update_paths.xml`). La prima volta Android chiede il permesso "installa app sconosciute" e l'installer parte
  al ritorno nell'app. Il vecchio APK si cancella all'avvio successivo.

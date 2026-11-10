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

Fuori scopo per ora: notifiche sulle nuove puntate, streaming/link ai player, social, tracciamento dei voti personali.

## 2. Stack

| Area | Scelta | Versione |
|---|---|---|
| Linguaggio | Kotlin | 2.0.20 |
| UI | Jetpack Compose + Material 3 (Material You su Android 12+) | BOM 2024.09.03 |
| Navigazione | Navigation Compose, rotte stringa | 2.8.2 |
| Architettura | MVVM, `StateFlow`, un `AppViewModel` condiviso + un ViewModel per Cerca e per Consigliati | Lifecycle 2.8.6 |
| DI | Manuale (`di/AppContainer.kt`), niente Hilt | — |
| Backend utente | Firebase Auth (anonimo) + Cloud Firestore | BoM 33.4.0 |
| Dati anime | AniList GraphQL, `https://graphql.anilist.co/` | pubblico, senza chiave |
| LLM (opzionale) | OpenRouter, `https://openrouter.ai/api/v1/` | chiave in `local.properties` |
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

## 3. Struttura del codice

```
app/src/main/java/com/example/animewatch/
├─ AnimeWatchApp.kt            crea AppContainer
├─ MainActivity.kt             setContent { Theme { AnimeWatchRoot } }
├─ di/AppContainer.kt          costruisce Retrofit, Firestore, repository, servizi
├─ data/
│  ├─ model/Models.kt          Anime, WatchProgress, AnimeList (+ regole: stagione completa, cache scaduta)
│  ├─ AnimeRepository.kt       memoria -> Firestore `anime/` -> AniList
│  ├─ firebase/UserDataSource  login anonimo, liste, progressi (flussi in tempo reale)
│  ├─ firebase/Mappers.kt      modelli <-> documenti Firestore
│  └─ remote/
│     ├─ anilist/              AniListApi (1 endpoint POST), AniListClient (query), DTO
│     └─ openrouter/           OpenRouterApi (chat/completions)
├─ domain/
│  ├─ RecommendationEngine     profilo generi + coseno (puro, testato)
│  ├─ RecommendationService    candidati da AniList -> ranking -> riordino AI opzionale
│  └─ LlmReranker              prompt a OpenRouter, parsing JSON, fallback
└─ ui/
   ├─ AnimeWatchRoot.kt        NavHost + bottom bar (Liste / Cerca / Consigliati) + banner errori
   ├─ AppViewModel.kt          utente, liste, progressi, cache schede, azioni
   ├─ components/Common.kt     AnimeRow, CoverImage, AddToListDialog, EmptyState
   ├─ lists/  detail/  search/  recs/   una cartella per schermata
   └─ theme/Theme.kt
```

Dipendenze ammesse: `ui -> domain/data`, `domain -> data`, `data` non conosce `ui`. `RecommendationEngine` non deve
importare nulla di Android (resta testabile sulla JVM).

## 4. Schermate

| Rotta | Contenuto |
|---|---|
| `lists` | Elenco liste (n. anime, n. stagioni viste), FAB "nuova lista", elimina con conferma |
| `list/{listId}` | Anime della lista con barra di avanzamento e spunta se stagione vista; rimuovi dalla lista |
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

users/{uid}/lists/{listId}           una lista personale
  name, createdAt (ms), animeIds[]   (arrayUnion/arrayRemove: niente duplicati)

users/{uid}/progress/{anilistId}     un documento per anime con almeno una puntata vista
  animeId, watchedEpisodes[], genres[], totalEpisodes, updatedAt (ms)
```

- `genres` è copiato nel progresso (denormalizzazione voluta): il profilo dei gusti si calcola leggendo solo `progress`.
- I campi null **non** si scrivono (le regole validano solo i campi presenti).
- Se non resta nessuna puntata vista il documento di progresso viene **cancellato**.
- TTL della cache: serie concluse 30 giorni, serie in corso 12 ore (escono nuove puntate). Se AniList non risponde si usa il dato scaduto.
- `firestore.rules`: `anime/` leggibile/scrivibile da utenti autenticati con validazione di campi, tipi e ID, **mai cancellabile**;
  `users/{uid}/**` solo dal proprietario.
- **Le scritture non si attendono** (`.await()` solo sulle letture). Firestore le applica subito alla cache locale; attenderle
  bloccherebbe la UI finché il server non conferma, cioè all'infinito offline. Gli errori finiscono nel log.

## 6. API esterne

**AniList** — un solo endpoint, `POST /` con `{query, variables}`. Tre query in `AniListClient`:
`search` (`media(search:…, sort: SEARCH_MATCH)`), `byIds` (`id_in`, max 50), `popularByGenre` (`genre:…`, `POPULARITY_DESC`, formati TV/MOVIE/OVA/ONA).
Sempre `isAdult: false` dove si cerca. Limite ~90 richieste/minuto: le chiamate sono ridotte da cache e debounce.

**OpenRouter** — `POST chat/completions`, header `Authorization: Bearer <chiave>`. Il modello è in `OPENROUTER_MODEL`
(default `openai/gpt-4o-mini`; gli ID modello cambiano nel tempo, verificarli su openrouter.ai/models).
Il modello riceve una **lista chiusa** di 30 candidati reali e deve restituire un array JSON `[{id, reason}]`:
non può inventare titoli. Ogni anomalia (rete, JSON non valido, ID sconosciuti) => `null` => si usa l'algoritmo.

## 7. Algoritmo dei consigliati

1. **Profilo.** Per ogni anime con puntate viste, peso = frazione vista (minimo 0,2); si somma il peso a ognuno dei suoi generi
   e si normalizza dividendo per il massimo. Risultato: vettore `genere -> [0,1]`.
2. **Candidati.** I 3 generi col peso maggiore: una richiesta AniList ciascuno (anime più popolari del genere, 40 per genere).
   Una richiesta per genere perché non è garantito se `genre_in` combini i generi in AND o in OR.
3. **Punteggio.** `0,75 · coseno(generi del candidato, profilo) + 0,25 · voto medio/100`. Il coseno misura quanto i generi
   del candidato puntano nella stessa direzione dei gusti dell'utente e non premia chi ha molti generi né chi ha guardato molto.
   Candidati senza alcun genere in comune scartati; già in lista o con progresso esclusi.
4. **Riordino AI (opzionale).** I primi 30 vanno a OpenRouter, che ne sceglie 12 con motivazione; senza chiave o in errore si mostrano i primi 20 dell'algoritmo.
5. **Quando si ricalcola.** All'apertura, al tocco su "Aggiorna", e solo se cambiano i 3 generi dominanti o l'interruttore AI
   (non a ogni puntata segnata: ogni calcolo costa richieste ad AniList).

## 8. Configurazione

### Firebase (obbligatorio)
1. Console Firebase -> nuovo progetto -> aggiungi app Android con package `com.example.animewatch`.
2. Scarica `google-services.json` in `app/` (è nel `.gitignore`; `app/google-services.json.example` mostra il formato).
3. Authentication -> metodo di accesso -> abilita **Anonimo**.
4. Firestore Database -> crea in modalità produzione, poi pubblica le regole: `firebase deploy --only firestore:rules`
   (usa `firebase.json` e `firestore.rules`) oppure incollale nella console.
5. Se cambi il package name, aggiornalo in `app/build.gradle.kts` (namespace e applicationId) e nella console Firebase.

### OpenRouter (opzionale)
Copia `local.properties.example` in `local.properties` e imposta `OPENROUTER_API_KEY` (e se vuoi `OPENROUTER_MODEL`).
Senza chiave l'interruttore AI non compare. La chiave finisce in `BuildConfig`: **non committare mai `local.properties`**.

### Build e test
```
gradle wrapper --gradle-version 8.9      # una volta sola, genera gradlew (il .jar non è nel repo)
./gradlew :app:testDebugUnitTest          # unit test del motore di raccomandazione
./gradlew :app:assembleDebug              # APK in app/build/outputs/apk/debug/
```
Android Studio (Koala o successivo) apre la cartella e fa tutto da solo. La CI (`.github/workflows/build.yml`) usa il
`google-services.json.example` come segnaposto, quindi compila senza segreti.

## 9. Sicurezza e limiti noti

- **Chiave OpenRouter nell'APK.** Chi decompila l'app può estrarla. Va bene per uso personale con un limite di spesa impostato su
  OpenRouter; per distribuire l'app la chiamata deve passare da un proxy (Cloud Function) che custodisce la chiave.
- **Cache `anime/` scrivibile dai client.** Le regole limitano forma e tipi ma un utente malevolo potrebbe scrivere dati falsi
  su un ID. Mitigazione futura: Firebase App Check, oppure scrittura solo da Cloud Function.
- **Login anonimo.** Nessuna schermata di accesso, ma disinstallando l'app l'UID, e quindi le liste, si perdono.
  Evoluzione prevista: collegare l'account anonimo a Google (`linkWithCredential`) per recuperare i dati su altri dispositivi.
- **Primo avvio offline.** L'accesso anonimo richiede rete la prima volta: l'app mostra un banner con "Riprova".
- **Sequel nei consigli.** Se l'utente guarda S1, la S2 (stessi generi) può comparire tra i consigliati. Soluzione prevista:
  leggere `relations` da AniList ed escludere le schede collegate a ciò che è già visto.
- **Titoli adulti.** Esclusi con `isAdult: false` lato AniList.
- **Pubblicazione.** `isMinifyEnabled = false` nella release: se si attiva R8 servono regole per kotlinx.serialization e Retrofit.

## 10. Stato della verifica

Il codice è stato scritto senza poter compilare né eseguire l'app (nessun Android SDK né accesso a rete nell'ambiente di
creazione). Il motore di raccomandazione ha unit test pensati per passare, ma **non sono mai stati eseguiti**, e le query
GraphQL non sono state provate contro il server reale. Al primo import in Android Studio:
1. Sync Gradle e `testDebugUnitTest`; correggere eventuali errori di compilazione.
2. Provare le tre query nel playground `https://anilist.co/graphiql` se una ricerca restituisce errori.
3. Verificare su dispositivo: crea lista, aggiungi anime, segna puntate, modalità aereo, consigliati.

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

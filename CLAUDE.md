# AnimeWatch — specifiche di progetto

App Android nativa per gestire gli anime da vedere: liste personali, progresso puntata per puntata,
anime consigliati in base ai generi già guardati. Dati anime da AniList. Persistenza **temporanea su file JSON**
in attesa del database vero (§5).

> Questo file è il contratto del progetto: chi lavora sul codice (persone o Claude) lo legge prima di
> modificare qualcosa e lo aggiorna se cambia una decisione.

## 1. Requisiti funzionali

1. **Liste.** L'utente crea più liste con nome libero (es. "Da vedere in inverno"), vi aggiunge e toglie anime, le elimina.
2. **Puntate.** Nella scheda di un anime ogni puntata è una chip che si segna/toglie come vista.
3. **Stagioni.** In AniList ogni stagione è una scheda a sé (S1, S2… sono ID diversi), quindi *stagione* = *scheda anime*.
   Una stagione risulta **vista** solo se è conclusa (`status = FINISHED`) **e** tutte le puntate sono segnate.
   Pulsante "Segna tutte come viste" per chiudere la stagione in un tocco; "Azzera" per annullare.
   Per le serie in corso le puntate disponibili sono quelle già uscite (`nextAiringEpisode - 1`).
4. **Progresso unico.** Il progresso è per anime, **non** per lista: lo stesso anime in due liste ha lo stesso stato.
5. **Cerca.** Ricerca per titolo (romaji/inglese) sul catalogo AniList, con debounce di 500 ms.
6. **Consigliati.** Sezione con anime dai generi simili a quelli già guardati (algoritmo in §7), esclusi quelli già in lista o iniziati.
   Riordino opzionale con un LLM via OpenRouter, che aggiunge un motivo in italiano per ogni consiglio.
7. **Funziona senza account e senza backend.** Nessun login: i dati stanno sul dispositivo (§5).

Fuori scopo per ora: sincronizzazione fra dispositivi, notifiche sulle nuove puntate, link ai player, social, voti personali.

## 2. Stack

| Area | Scelta | Versione |
|---|---|---|
| Linguaggio | Kotlin | 2.0.20 |
| UI | Jetpack Compose + Material 3 (Material You su Android 12+) | BOM 2024.09.03 |
| Navigazione | Navigation Compose, rotte stringa | 2.8.2 |
| Architettura | MVVM, `StateFlow`, un `AppViewModel` condiviso + un ViewModel per Cerca e per Consigliati | Lifecycle 2.8.6 |
| DI | Manuale (`di/AppContainer.kt`), niente Hilt | — |
| Persistenza (temporanea) | File JSON in `filesDir/database/` via kotlinx.serialization | 1.7.3 |
| Dati anime | AniList GraphQL, `https://graphql.anilist.co/` | pubblico, senza chiave |
| LLM (opzionale) | OpenRouter, `https://openrouter.ai/api/v1/` | chiave in `local.properties` |
| Rete | Retrofit + OkHttp + kotlinx.serialization | 2.11.0 / 4.12.0 / 1.7.3 |
| Immagini | Coil | 2.7.0 |
| Build | Gradle 8.9, AGP 8.5.2, version catalog | JDK 17 |
| SDK Android | minSdk 26, targetSdk/compileSdk 34 | — |
| Test | JUnit 4 (unit test JVM sul motore di raccomandazione) | 4.13.2 |

### Perché Kotlin e non Go
Go su Android passa da `gomobile` o Fyne: una UI ricca e nativa (Compose, Material You, accessibilità) in Go sarebbe un
ripiego, e le librerie di piattaforma sono pensate per Kotlin/Java. Se serve codice Go, il posto giusto è un backend dietro l'app.

### Perché AniList
Non esiste un database pubblico di anime "già pronto" dentro Firebase o simili. I cataloghi pubblici esistenti sono AniList
(GraphQL), Jikan (dati MyAnimeList, rate limit stretto, non ufficiale) e Kitsu. AniList è stato scelto perché: nessuna chiave,
generi e stato della serie affidabili, numero puntate e prossima puntata in corso, copertine, ricerca per titolo.

## 3. Struttura del codice

```
app/src/main/java/com/example/animewatch/
├─ AnimeWatchApp.kt            crea AppContainer
├─ MainActivity.kt             setContent { Theme { AnimeWatchRoot } }
├─ di/AppContainer.kt          costruisce Retrofit, store, repository, servizi  <- QUI si cambia DB
├─ data/
│  ├─ Stores.kt                interfacce UserStore e AnimeStore = punto di sostituzione del DB
│  ├─ model/Models.kt          Anime, WatchProgress, AnimeList (+ regole: stagione completa, cache scaduta)
│  ├─ AnimeRepository.kt       memoria -> AnimeStore -> AniList
│  ├─ local/                   PERSISTENZA TEMPORANEA: JsonStore, JsonUserStore, JsonAnimeStore (da eliminare)
│  └─ remote/
│     ├─ anilist/              AniListApi (1 endpoint POST), AniListClient (query), DTO
│     └─ openrouter/           OpenRouterApi (chat/completions)
├─ domain/
│  ├─ RecommendationEngine     profilo generi + coseno (puro, testato)
│  ├─ RecommendationService    candidati da AniList -> ranking -> riordino AI opzionale
│  └─ LlmReranker              prompt a OpenRouter, parsing JSON, fallback
└─ ui/
   ├─ AnimeWatchRoot.kt        NavHost + bottom bar (Liste / Cerca / Consigliati)
   ├─ AppViewModel.kt          liste, progressi, cache schede, azioni
   ├─ components/Common.kt     AnimeRow, CoverImage, AddToListDialog, EmptyState
   ├─ lists/  detail/  search/  recs/   una cartella per schermata
   └─ theme/Theme.kt
```

Dipendenze ammesse: `ui -> domain/data`, `domain -> data`, `data` non conosce `ui`. `RecommendationEngine` non deve
importare nulla di Android (resta testabile sulla JVM). Nessun'altra classe deve nominare `data/local/`: solo `AppContainer`.

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

## 5. Persistenza temporanea (workaround) e sostituzione col DB vero

**Situazione attuale.** Finché lo sviluppatore del database non lo mette a disposizione, l'app salva tutto in file JSON
nella cartella `database/` della memoria privata dell'app (`context.filesDir/database/`):

```
database/anime/{anilistId}.json   una scheda per file = risultato delle chiamate ad AniList (cache)
database/lists.json               tutte le liste: [{id, name, createdAt, animeIds[]}]
database/progress.json            tutti i progressi: [{animeId, watchedEpisodes[], genres[], totalEpisodes, updatedAt}]
```

- **La cartella sta sul dispositivo, non nel repository.** Un'app Android non può scrivere nella cartella del progetto sul PC.
  Per vedere i file: Android Studio -> *View -> Tool Windows -> Device Explorer* -> `data/data/com.example.animewatch/files/database`
  (tasto destro -> *Save As* per copiarli sul PC). Funziona su emulatore e su telefono con build debug.
- **Per azzerare i dati:** disinstalla l'app, oppure *Impostazioni -> App -> AnimeWatch -> Archiviazione -> Cancella dati*.
- **Cache anime:** una scheda è valida 30 giorni se la serie è conclusa, 12 ore se è in corso (escono nuove puntate).
  Se AniList non risponde si usa il dato scaduto. Ogni risposta di AniList (anche le ricerche e i candidati dei consigliati)
  viene salvata.
- **Le scritture non bloccano la UI:** liste e progressi vivono in memoria come `StateFlow` (la UI si aggiorna subito) e il
  file viene riscritto in background, passando da un `.tmp` e rinominando, così un crash non lascia file troncati.
- **Limiti del workaround:** nessuna sincronizzazione fra dispositivi, dati persi disinstallando l'app, un file illeggibile
  viene trattato come vuoto, nessuna migrazione fra versioni dello schema (se si cambiano i modelli, cancellare i dati).

**Come passare al database vero** (contratto per chi lo implementa):
1. Implementare `UserStore` e `AnimeStore` (`data/Stores.kt`, documentate riga per riga). `UserStore` espone `Flow` che
   devono riemettere a ogni modifica; le scritture sono non sospensive e non devono bloccare il chiamante.
2. In `di/AppContainer.kt` sostituire le righe tra i commenti `PERSISTENZA TEMPORANEA` con le nuove implementazioni.
3. Eliminare `data/local/`, e se non serve più la serializzazione, `@Serializable` dai modelli in `Models.kt`.
4. Se il DB è multiutente, `UserStore` riceverà l'identità dal proprio livello di accesso: oggi non esiste il concetto di utente.

Una versione precedente dell'app con Firebase (Auth anonimo + Firestore con cache condivisa e regole di sicurezza) è nella
cronologia git, commit `Primo commit: AnimeWatch`: è un esempio funzionante di implementazione delle stesse interfacce.

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

## 8. Configurazione, build e test

Nessuna configurazione obbligatoria: niente account, niente file di segreti. Apri la cartella in Android Studio (Koala o successivo) e premi Run.

### OpenRouter (opzionale)
Copia `local.properties.example` in `local.properties` e imposta `OPENROUTER_API_KEY` (e se vuoi `OPENROUTER_MODEL`).
Senza chiave l'interruttore AI non compare. La chiave finisce in `BuildConfig`: **non committare mai `local.properties`**.

### Da terminale
```
gradle wrapper --gradle-version 8.9      # una volta sola, genera gradlew (il .jar non è nel repo)
./gradlew :app:testDebugUnitTest          # unit test del motore di raccomandazione
./gradlew :app:assembleDebug              # APK in app/build/outputs/apk/debug/
```
La CI (`.github/workflows/build.yml`) esegue gli stessi due comandi ad ogni push.

## 9. Sicurezza e limiti noti

- **Chiave OpenRouter nell'APK.** Chi decompila l'app può estrarla. Va bene per uso personale con un limite di spesa impostato su
  OpenRouter; per distribuire l'app la chiamata deve passare da un proxy che custodisce la chiave.
- **Dati in chiaro sul dispositivo.** I JSON sono nella memoria privata dell'app (non leggibili da altre app senza root) ma non
  cifrati. Contengono solo liste e puntate viste, nessun dato personale.
- **Sequel nei consigli.** Se l'utente guarda S1, la S2 (stessi generi) può comparire tra i consigliati. Soluzione prevista:
  leggere `relations` da AniList ed escludere le schede collegate a ciò che è già visto.
- **Titoli adulti.** Esclusi con `isAdult: false` lato AniList.
- **Pubblicazione.** `isMinifyEnabled = false` nella release: se si attiva R8 servono regole per kotlinx.serialization e Retrofit.

## 10. Stato della verifica

Il codice è stato scritto senza poter compilare né eseguire l'app (nessun Android SDK né accesso a rete nell'ambiente di
creazione). Il motore di raccomandazione ha unit test pensati per passare, ma **non sono mai stati eseguiti**, e le query
GraphQL non sono state provate contro il server reale. La persistenza JSON non ha test. Al primo import in Android Studio:
1. Sync Gradle e `testDebugUnitTest`; correggere eventuali errori di compilazione.
2. Provare le tre query nel playground `https://anilist.co/graphiql` se una ricerca restituisce errori.
3. Verificare su dispositivo: crea lista, aggiungi anime, segna puntate, chiudi e riapri l'app (i dati devono restare), consigliati.

## 11. Convenzioni

- Kotlin ufficiale (`kotlin.code.style=official`), 4 spazi, righe ragionevoli (~120).
- Nessuna logica di business nei composable: azioni nei ViewModel, regole di dominio in `data/model` o `domain`.
- Gli stati sono `StateFlow` immutabili; le schermate usano `collectAsStateWithLifecycle()`.
- Ogni nuova query AniList passa da `AniListClient` e restituisce `Anime`.
- Ogni modifica ai modelli salvati (`Anime`, `WatchProgress`, `AnimeList`) richiede di cancellare i dati del dispositivo o gestire la migrazione.
- I commenti spiegano il *perché* di una scelta non ovvia, non ripetono il codice.
- Mai committare `local.properties`, keystore, chiavi.

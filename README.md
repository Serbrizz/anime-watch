# AnimeWatch

App Android (Kotlin + Jetpack Compose) per tenere traccia degli anime da vedere: liste, puntate viste, consigli in base ai generi.
Dati anime da [AniList](https://anilist.co), dati utente su Firebase (accesso con Google + Firestore), riordino AI opzionale con OpenRouter.

Specifiche complete, architettura e decisioni: **[CLAUDE.md](CLAUDE.md)**.

## Avvio rapido

1. Crea un progetto Firebase, aggiungi l'app Android `com.example.animewatch`, abilita l'accesso con **Google** e **Firestore**.
2. Registra lo SHA-1 del keystore di debug nell'app Firebase (dettagli in CLAUDE.md §8).
3. Scarica `google-services.json` in `app/` (dopo i passi 1 e 2) e pubblica `firestore.rules`.
4. (Opzionale) copia `local.properties.example` in `local.properties` e inserisci `OPENROUTER_API_KEY`.
5. Apri la cartella in Android Studio e premi Run. Da terminale: `gradle wrapper --gradle-version 8.9` una volta, poi `./gradlew :app:assembleDebug`.

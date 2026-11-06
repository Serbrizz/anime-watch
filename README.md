# AnimeWatch

App Android (Kotlin + Jetpack Compose) per tenere traccia degli anime da vedere: liste, puntate viste, consigli in base ai generi.
Dati anime da [AniList](https://anilist.co), dati utente su Firebase (Auth anonimo + Firestore), riordino AI opzionale con OpenRouter.

Specifiche complete, architettura e decisioni: **[CLAUDE.md](CLAUDE.md)**.

## Avvio rapido

1. Crea un progetto Firebase, aggiungi l'app Android `com.example.animewatch`, abilita **Auth anonimo** e **Firestore**.
2. Scarica `google-services.json` in `app/` e pubblica `firestore.rules`.
3. (Opzionale) copia `local.properties.example` in `local.properties` e inserisci `OPENROUTER_API_KEY`.
4. Apri la cartella in Android Studio e premi Run. Da terminale: `gradle wrapper --gradle-version 8.9` una volta, poi `./gradlew :app:assembleDebug`.

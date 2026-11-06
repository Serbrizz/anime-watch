# AnimeWatch

App Android (Kotlin + Jetpack Compose) per tenere traccia degli anime da vedere: liste, puntate viste, consigli in base ai generi.
Dati anime da [AniList](https://anilist.co); liste e progressi salvati in file JSON sul dispositivo finché non c'è il database vero;
riordino AI dei consigli opzionale con OpenRouter.

Specifiche complete, architettura e come sostituire la persistenza temporanea: **[CLAUDE.md](CLAUDE.md)**.

## Avvio rapido

1. Apri la cartella in Android Studio (Koala o successivo) e attendi il Sync Gradle.
2. Premi Run su un emulatore o su un telefono con debug USB. Non serve nessuna configurazione.
3. (Opzionale) copia `local.properties.example` in `local.properties` e inserisci `OPENROUTER_API_KEY`.

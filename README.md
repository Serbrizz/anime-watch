# AnimeWatch

App Android (Kotlin + Jetpack Compose) per tenere traccia degli anime da vedere: liste, puntate viste, consigli in base ai generi.
Dati anime da [AniList](https://anilist.co), dati utente su Firebase (accesso con Google + Firestore), riordino AI con OpenRouter.

Specifiche complete, architettura e decisioni: **[CLAUDE.md](CLAUDE.md)**.

## Installare l'app

Scarica `AnimeWatch.apk` dall'ultima [release](https://github.com/Serbrizz/anime-watch/releases/latest) e aprilo sul telefono.
Le versioni successive arrivano dall'app stessa: all'uscita di una nuova versione compare un popup per aggiornare.

## Lavorare sul progetto

Il progetto Firebase esiste già e tutti firmano gli APK con **lo stesso keystore**, quindi non si registrano SHA-1 nuovi.
Chiedi in privato a chi gestisce le release:

- il **keystore** condiviso (da tenere fuori dal repo);
- `google-services.json`, che puoi anche scaricare dalla console Firebase se hai accesso al progetto.

Non serve nessuna chiave OpenRouter: l'app passa da un proxy (`proxy/`, Cloudflare Worker) che la custodisce.

Poi:

1. Copia `keystore.properties.example` in `keystore.properties` e indica il percorso del keystore (con `/`).
   Senza questo file, o con un altro keystore, la build si ferma con un messaggio che spiega cosa manca.
2. Metti `google-services.json` in `app/`.
3. Apri la cartella in Android Studio (JDK 17 o successivo) e premi Run. Da terminale: `.\gradlew.bat :app:assembleDebug`,
   oppure `.\scripts\install.ps1` per aggiornare il codice, compilare e installare sul telefono collegato.

Si lavora su `dev`; `main` riceve solo i merge per le release (CLAUDE.md §12).

## Script

Tutti gli script di sviluppo (installazione sul telefono, test, logcat, deploy di regole e proxy, release) stanno in
`scripts/`. `.\gui.cmd` nella radice, anche con doppio clic, apre una finestra che li elenca con le loro opzioni e li lancia.
Per aggiungerne uno basta un nuovo `.ps1` in `scripts/` (CLAUDE.md §8, "Script e GUI").

## Pubblicare una versione

Su Windows, con [GitHub CLI](https://cli.github.com) autenticata (`gh auth login`) e i file del passo precedente:

```
git checkout main
git merge dev
.\scripts\release.cmd patch      # oppure minor / major
```

Lo script controlla la configurazione e apre il Blocco note per le note di rilascio, che gli utenti vedranno nel popup.
Poi incrementa la versione, lancia i test, compila e firma l'APK, chiede conferma, fa commit, tag e push, pubblica la
release su GitHub e riporta `dev` alla release. Se qualcosa va storto prima del commit rimette tutto com'era.
Dettagli in CLAUDE.md §13.

## Proxy AI

Il riordino AI dei consigliati passa dal Worker in `proxy/`, che verifica l'utente e aggiunge la chiave OpenRouter.
Si modifica e si pubblica da `proxy/` con `npm install`, `npx wrangler login` e `npm run deploy` (CLAUDE.md §8).
Serve solo per cambiare il proxy; per lavorare sull'app non serve.

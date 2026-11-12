<#
.SYNOPSIS
    Pubblica su Cloudflare la versione attuale del proxy (wrangler deploy).
.DESCRIPTION
    Carica il codice di proxy\ e la sua configurazione (wrangler.toml: modelli di Gemini, limiti di richieste...) sul
    Worker animewatch-ai. Le modifiche valgono subito per tutti gli utenti, senza una nuova versione dell'app.

    Le chiavi di Gemini e TMDB non cambiano: per quelle c'è "Imposta una chiave del proxy".

    La prima volta installa wrangler (npm install). L'accesso a Cloudflare si fa una volta per PC, con
    "Accedi a Cloudflare".

    Serve: Node.js e l'accesso all'account Cloudflare del proxy (chi lo gestisce può invitarti come membro).
.NOTES
    1. Lancia prima "Test del proxy".
    2. Premi Esegui.
    3. Se l'output dice che non hai fatto l'accesso a Cloudflare, lancia prima "Accedi a Cloudflare", poi di nuovo
       questo.
    4. Aspetta la scritta verde FATTO: sopra, l'output mostra l'indirizzo del Worker.
.FUNCTIONALITY
    Deploy del proxy
.COMPONENT
    Proxy
.EXAMPLE
    .\scripts\proxy-deploy.ps1
#>
Push-Location (Join-Path (Split-Path -Parent $PSScriptRoot) 'proxy')
try {
    if (-not (Test-Path node_modules)) {
        & npm.cmd install
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }
    & npm.cmd run deploy
    exit $LASTEXITCODE
} finally {
    Pop-Location
}

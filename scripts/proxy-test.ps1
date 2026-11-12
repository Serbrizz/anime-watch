<#
.SYNOPSIS
    Lancia i test del proxy (il Cloudflare Worker) sul PC, con Node.
.DESCRIPTION
    Il proxy è il Cloudflare Worker che sta fra l'app e i servizi esterni: custodisce le chiavi di Gemini e TMDB,
    verifica che chi chiama sia un utente dell'app e limita le richieste.

    I test controllano la verifica dei token di Firebase (firma, scadenza, progetto) e le risposte di TMDB ridotte ai
    campi che usa l'app. Girano sul PC e non toccano il proxy online.

    Conviene lanciarli prima di "Deploy del proxy".

    Serve: Node.js.
.NOTES
    1. Premi Esegui.
    2. Aspetta la fine: FATTO in verde vuol dire che tutti i test passano.
    3. Con ERRORE in rosso, l'output mostra quale test fallisce e perché.
.FUNCTIONALITY
    Test del proxy
.COMPONENT
    Proxy
.EXAMPLE
    .\scripts\proxy-test.ps1
#>
Push-Location (Join-Path (Split-Path -Parent $PSScriptRoot) 'proxy')
try {
    & node --test
    exit $LASTEXITCODE
} finally {
    Pop-Location
}

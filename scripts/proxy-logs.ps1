<#
.SYNOPSIS
    Mostra in tempo reale i log del proxy (wrangler tail).
.DESCRIPTION
    Per ogni richiesta dell'app al proxy mostra cosa è successo: per i riordini di Gemini il modello usato, il motivo
    di arresto e i token; poi gli errori: quota di Gemini finita (429), Gemini sovraccarico (503), token rifiutati
    (401), errori di TMDB. Non mostra uid né prompt.

    Si vede solo quello che succede mentre è in corso. Lo storico degli ultimi giorni è nella dashboard di
    Cloudflare: Workers > animewatch-ai > Logs.

    Serve: Node.js e l'accesso all'account Cloudflare del proxy.
.NOTES
    1. Premi Esegui. Se l'output dice che non hai fatto l'accesso a Cloudflare, lancia prima "Accedi a Cloudflare".
    2. Aspetta che l'output dica di essere in attesa dei log, poi usa l'app: ogni richiesta compare dopo qualche
       secondo.
    3. Per fermare premi Ferma.
.FUNCTIONALITY
    Log del proxy
.COMPONENT
    Proxy
.EXAMPLE
    .\scripts\proxy-logs.ps1
#>
Push-Location (Join-Path (Split-Path -Parent $PSScriptRoot) 'proxy')
try {
    if (-not (Test-Path node_modules)) {
        & npm.cmd install
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }
    # --format pretty: senza un terminale (nella GUI) wrangler scriverebbe JSON.
    & npx.cmd wrangler tail --format pretty
    exit $LASTEXITCODE
} finally {
    Pop-Location
}

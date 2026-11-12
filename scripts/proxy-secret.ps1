<#
.SYNOPSIS
    Salva sul proxy, come segreto di Cloudflare, la chiave API di Gemini o di TMDB.
.DESCRIPTION
    Le chiavi stanno solo sul Worker, cifrate: non sono nell'APK né nel repository, quindi nessuno può estrarle
    dall'app.

    Serve la prima volta che si configura il proxy, o quando una chiave cambia (revocata, nuovo progetto...). La
    nuova chiave vale subito, senza deploy.

    La chiave Gemini va creata su un progetto Google Cloud senza fatturazione: così resta sul piano gratuito e non
    può costare nulla.

    Serve: Node.js e l'accesso all'account Cloudflare del proxy (una volta per PC, con "Accedi a Cloudflare").
.NOTES
    1. Procurati la chiave. Gemini: aistudio.google.com/apikey > Crea chiave API, scegliendo un progetto senza
       fatturazione. TMDB: themoviedb.org > Impostazioni > API > "Chiave API" (la v3, non il token di lettura).
    2. Scegli quale chiave stai impostando nella colonna Opzioni, a destra.
    3. Premi Esegui. Quando l'output chiede la chiave, incollala nel campo in basso (Ctrl+V) e premi Invio: non
       viene mostrata.
    4. Aspetta "Success!" e la scritta verde FATTO.
.PARAMETER Name
    Quale chiave
    GEMINI_API_KEY: la chiave di Gemini, per il riordino dei consigliati.

    TMDB_API_KEY: la chiave di TMDB, per titoli, trame e miniature delle puntate.
.FUNCTIONALITY
    Imposta una chiave del proxy
.COMPONENT
    Proxy
.EXAMPLE
    .\scripts\proxy-secret.ps1 -Name TMDB_API_KEY
#>
param(
    [Parameter(Mandatory)]
    [ValidateSet('GEMINI_API_KEY', 'TMDB_API_KEY')]
    [string]$Name
)
Push-Location (Join-Path (Split-Path -Parent $PSScriptRoot) 'proxy')
try {
    if (-not (Test-Path node_modules)) {
        & npm.cmd install
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }
    # La chiave la chiede lo script e non wrangler: senza un terminale (nella GUI) wrangler la legge dallo standard
    # input. Nella GUI la si legge senza eco (Read-Host la riscriverebbe nell'output); in una console, nascosta.
    if ([Console]::IsInputRedirected) {
        Write-Host "Chiave $Name (non viene mostrata): " -NoNewline
        $key = [Console]::In.ReadLine()
        Write-Host ''
    } else {
        $secure = Read-Host "Chiave $Name (non viene mostrata)" -AsSecureString
        $key = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
    }
    if (-not "$key".Trim()) {
        Write-Host 'Nessuna chiave inserita: non cambio nulla.' -ForegroundColor Yellow
        exit 1
    }
    "$key".Trim() | & npx.cmd wrangler secret put $Name
    exit $LASTEXITCODE
} finally {
    Pop-Location
}

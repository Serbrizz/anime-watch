<#
.SYNOPSIS
    Pubblica firestore.rules sul progetto Firebase: le regole di sicurezza del database.
.DESCRIPTION
    Le regole decidono chi può leggere e scrivere ogni documento di Firestore: la cache condivisa di anime e puntate,
    le liste (secondo il ruolo di ognuno), gli inviti e i progressi di ogni utente.

    Vanno pubblicate ogni volta che cambi firestore.rules, per esempio quando aggiungi un campo ad Anime: finché non
    lo fai, il database usa le regole di prima e rifiuta le scritture con i campi nuovi.

    Valgono subito per tutti gli utenti, quindi una regola sbagliata può bloccare l'app a tutti: controllale prima.

    Serve: la Firebase CLI installata e collegata a un account con accesso al progetto.
.NOTES
    1. Solo la prima volta su questo PC: in un terminale scrivi npm install -g firebase-tools, poi firebase login e
       accedi nel browser con l'account del progetto.
    2. Premi Esegui.
    3. Aspetta "Deploy complete!" e la scritta verde FATTO. Se le regole hanno un errore, l'output indica la riga
       e non pubblica nulla.
.FUNCTIONALITY
    Pubblica le regole Firestore
.COMPONENT
    Firebase
.EXAMPLE
    .\scripts\deploy-rules.ps1
#>
$root = Split-Path -Parent $PSScriptRoot
if (-not (Get-Command firebase -ErrorAction SilentlyContinue)) {
    Write-Host 'Firebase CLI non trovata: npm install -g firebase-tools, poi firebase login.' -ForegroundColor Red
    exit 1
}
Push-Location $root
try {
    & firebase deploy --only firestore:rules
    exit $LASTEXITCODE
} finally {
    Pop-Location
}

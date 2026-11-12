<#
.SYNOPSIS
    Mostra in diretta il logcat di AnimeWatch: i messaggi che l'app scrive mentre la usi.
.DESCRIPTION
    Mostra solo le righe del processo di AnimeWatch, non quelle del resto del telefono. Se l'app non è aperta la
    avvia e aspetta che parta.

    Ci sono anche gli errori di rete e di AniList, che non arrivano a Crashlytics: è il posto giusto per capire un
    problema appena successo.

    Il filtro è sul processo: se l'app si chiude, va in crash o la riavvii, le righe nuove non compaiono più e
    bisogna rilanciare lo script.

    Serve: il telefono collegato (cavo o Wi-Fi) con il debug USB autorizzato.
.NOTES
    1. Collega il telefono, con il cavo o via Wi-Fi.
    2. Premi Esegui: l'output compare nel pannello in basso e, se serve, si apre AnimeWatch sul telefono.
    3. Usa l'app e riproduci il problema: i messaggi compaiono subito nell'output.
    4. Per copiare il log premi "Copia output", oppure seleziona alcune righe e premi Ctrl+C.
    5. Per fermare premi Ferma.
.PARAMETER Serial
    Telefono
    Serve solo se sono collegati più dispositivi: scrivi il seriale di quello giusto (li elenca "Installa sul
    telefono" quando ne trova più di uno). Vuoto: l'unico collegato, o quello della variabile ANDROID_SERIAL.
.FUNCTIONALITY
    Logcat dell'app
.COMPONENT
    Telefono
.EXAMPLE
    .\scripts\logcat.ps1
#>
param(
    [string]$Serial = $env:ANDROID_SERIAL
)
$ErrorActionPreference = 'Continue'
$package = 'com.example.animewatch'
$root = Split-Path -Parent $PSScriptRoot

function Find-Adb {
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $sdks = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)
    $props = Join-Path $root 'local.properties'
    if (Test-Path $props) {
        $line = Get-Content $props | Where-Object { $_ -match '^\s*sdk\.dir\s*=' } | Select-Object -First 1
        if ($line) { $sdks += ($line -replace '^\s*sdk\.dir\s*=\s*', '' -replace '\\:', ':' -replace '\\\\', '\') }
    }
    foreach ($sdk in $sdks) {
        if (-not $sdk) { continue }
        $path = Join-Path $sdk 'platform-tools\adb.exe'
        if (Test-Path $path) { return $path }
    }
    return $null
}

$adb = Find-Adb
if (-not $adb) {
    Write-Host "adb non trovato: installa le platform-tools dell'Android SDK (con Android Studio)." -ForegroundColor Red
    exit 1
}
$target = @()
if ($Serial) { $target = @('-s', $Serial) }

function Get-AppPid { "$(& $adb @target shell pidof $package 2>$null)".Trim() }

$appPid = Get-AppPid
if (-not $appPid) {
    Write-Host "Avvio $package..."
    & $adb @target shell am start -n "$package/.MainActivity" | Out-Null
    if ($LASTEXITCODE -ne 0) { Write-Host 'Non riesco ad avviare l''app: il telefono è collegato?' -ForegroundColor Red; exit 1 }
    for ($i = 0; $i -lt 20 -and -not $appPid; $i++) {
        Start-Sleep -Milliseconds 500
        $appPid = Get-AppPid
    }
    if (-not $appPid) { Write-Host 'L''app non risulta avviata.' -ForegroundColor Red; exit 1 }
}
Write-Host "Logcat del processo $appPid (Ctrl+C per fermare)" -ForegroundColor Cyan
& $adb @target logcat "--pid=$appPid"

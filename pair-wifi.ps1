<#
.SYNOPSIS
    Collega il telefono ad adb via Wi-Fi (debug wireless), così install.ps1 e logcat funzionano senza cavo.
.DESCRIPTION
    Se il telefono è collegato via USB accende da solo il debug wireless; altrimenti va acceso a mano sul telefono.
    Trova indirizzo e porta via mDNS (la porta cambia a ogni accensione) e fa adb connect.
    Se il PC non è ancora abbinato al telefono, guida l'abbinamento: si apre "Abbina dispositivo con codice" sul
    telefono e si scrive il codice; indirizzo e porta li trova lo script.
.EXAMPLE
    .\pair-wifi.ps1
#>
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Continue'

function Find-Adb {
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $sdks = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)
    $props = Join-Path $PSScriptRoot 'local.properties'
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

function Fail([string]$reason) {
    Write-Host $reason -ForegroundColor Red
    exit 1
}

function Ask([string]$prompt) {
    try { return (Read-Host $prompt -ErrorAction Stop).Trim() }
    catch { Fail "Non posso chiedere '$prompt': lancia lo script da un terminale interattivo." }
}

# Indirizzi "ip:porta" dei servizi mDNS di quel tipo; con il cavo, solo quelli del telefono collegato.
function Find-Services([string]$type) {
    $lines = @(& $adb mdns services 2>&1 | ForEach-Object { "$_" } | Where-Object { $_ -match [regex]::Escape($type) })
    if ($script:usbSerial) {
        $own = @($lines | Where-Object { $_ -match [regex]::Escape("adb-$($script:usbSerial)-") })
        if ($own.Count -gt 0) { $lines = $own }
    }
    $lines | ForEach-Object { ($_ -split '\s+')[-1] }
}

# La cache mDNS di adb può ricordare per qualche secondo la porta di prima: si provano tutte finché una risponde.
# Se il telefono si vede ma rifiuta per qualche secondo di fila, di solito il PC non è abbinato: inutile aspettare oltre.
function Connect-Wifi([int]$seconds) {
    $deadline = (Get-Date).AddSeconds($seconds)
    $firstSeen = $null
    while ((Get-Date) -lt $deadline) {
        $addresses = @(Find-Services '_adb-tls-connect._tcp')
        if ($addresses.Count -gt 0 -and -not $firstSeen) { $firstSeen = Get-Date; $script:seen = $true }
        foreach ($address in $addresses) {
            $out = (& $adb connect $address 2>&1 | Out-String).Trim()
            if ($out -match '^(connected|already connected)') {
                Write-Host "Collegato via Wi-Fi: $address" -ForegroundColor Green
                if ($script:usbSerial) { Write-Host 'Puoi scollegare il cavo. Con entrambi collegati, install.ps1 vuole -Serial.' }
                return $true
            }
        }
        if ($firstSeen -and ((Get-Date) - $firstSeen).TotalSeconds -gt 8) { return $false }
        Start-Sleep -Seconds 1
    }
    return $false
}

function Start-Pairing {
    Write-Host ''
    if ($script:seen) {
        Write-Host 'Il telefono rifiuta la connessione: il PC non è abbinato. Sul telefono apri:' -ForegroundColor Yellow
    } else {
        Write-Host 'Non vedo il telefono sulla rete (stesso Wi-Fi del PC?). Se il PC non è mai stato abbinato, sul telefono apri:' -ForegroundColor Yellow
    }
    Write-Host '    Opzioni sviluppatore -> Debug wireless -> Abbina dispositivo con codice' -ForegroundColor Yellow
    Write-Host 'e lascia aperta la finestra col codice.'
    Write-Host 'Attendo che il telefono si annunci sulla rete...'
    $address = $null
    $deadline = (Get-Date).AddSeconds(60)
    while ((Get-Date) -lt $deadline -and -not $address) {
        $address = @(Find-Services '_adb-tls-pairing._tcp') | Select-Object -First 1
        if (-not $address) { Start-Sleep -Seconds 1 }
    }
    if ($address) {
        Write-Host "Trovato: $address"
    } else {
        Write-Host 'Non lo trovo da solo (rete che blocca mDNS?).'
        $address = Ask 'Scrivi indirizzo e porta mostrati sotto il codice (es. 192.168.1.20:37123)'
        if ($address -notmatch '^\S+:\d+$') { Fail "Indirizzo non valido: '$address'." }
    }
    $code = Ask 'Codice di abbinamento (6 cifre)'
    if ($code -notmatch '^\d{6}$') { Fail "Codice non valido: '$code'." }
    $out = (& $adb pair $address $code 2>&1 | Out-String).Trim()
    if ($out -notmatch 'Successfully paired') { Fail "Abbinamento non riuscito: $out" }
    Write-Host 'Abbinato.' -ForegroundColor Green
}

$adb = Find-Adb
if (-not $adb) { Fail 'adb non trovato: aggiungi platform-tools dell''Android SDK al PATH o imposta ANDROID_HOME.' }

$devices = @(& $adb devices 2>&1 | ForEach-Object { "$_" } | Where-Object { $_ -match '^(\S+)\tdevice$' } |
    ForEach-Object { ($_ -split '\t')[0] })
# I collegamenti Wi-Fi hanno per seriale "ip:porta" o il nome mDNS; quelli USB il numero di serie.
$wireless = @($devices | Where-Object { $_ -match ':|_adb-tls-connect' })
if ($wireless.Count -gt 0) {
    Write-Host "Già collegato via Wi-Fi: $($wireless -join ', ')" -ForegroundColor Green
    exit 0
}

$script:seen = $false
$script:usbSerial = $devices | Select-Object -First 1
if ($script:usbSerial) {
    Write-Host "Accendo il debug wireless su $($script:usbSerial)..."
    & $adb -s $script:usbSerial shell settings put global adb_wifi_enabled 1 | Out-Null
} else {
    Write-Host 'Nessun telefono col cavo: il debug wireless deve essere acceso sul telefono (Opzioni sviluppatore -> Debug wireless).'
}

Write-Host 'Cerco il telefono sulla rete...'
if (Connect-Wifi 20) { exit 0 }

Start-Pairing
if (Connect-Wifi 20) { exit 0 }
Fail 'Abbinato, ma il telefono non accetta la connessione: spegni e riaccendi Debug wireless e rilancia lo script.'

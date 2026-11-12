<#
.SYNOPSIS
    Aggiorna il codice da GitHub, compila l'APK e lo installa sul telefono collegato.
.DESCRIPTION
    Fa in sequenza:
    - controlla che git, adb e il telefono siano pronti: telefono collegato e autorizzato, uno solo oppure quello
      scelto in "Telefono";
    - scarica le modifiche del branch su cui sei (git fetch e git pull): se hai commit locali non pubblicati e su
      GitHub ce ne sono di nuovi si ferma, invece di unirli;
    - compila l'APK di debug, o di release se accendi l'opzione;
    - lo installa sopra la versione già presente sul telefono: i dati dell'app restano e non serve rifare l'accesso.

    Si ferma al primo problema, dice in quale passo e perché, e non esegue i passi successivi. Il pulsante Ferma lo interrompe in qualsiasi
    momento.

    Serve: il telefono con il debug USB attivo (Impostazioni > Opzioni sviluppatore > Debug USB), collegato con il cavo
    o via Wi-Fi.
.NOTES
    1. Collega il telefono al PC con il cavo USB, oppure via Wi-Fi con "Collega il telefono via Wi-Fi".
    2. Sblocca il telefono. La prima volta che lo colleghi a questo PC compare "Consentire il debug USB?": tocca
       Consenti (meglio con "Consenti sempre da questo computer").
    3. Premi Esegui. La compilazione richiede da uno a qualche minuto.
    4. Quando l'output scrive in verde "Fatto: AnimeWatch ... installata", apri l'app sul telefono.
    5. Se invece si ferma con INTERROTTO, leggi il motivo: dice cosa sistemare. Poi premi di nuovo Esegui.
.PARAMETER Release
    APK di release
    Acceso compila la variante release, quella che ricevono gli utenti: richiede il vero google-services.json.
    Spento compila la debug, che ha la stessa firma e quindi si installa anche sopra una versione pubblicata: di
    solito si lascia spento.
.PARAMETER Serial
    Telefono
    Serve solo se sono collegati più dispositivi (per esempio lo stesso telefono con il cavo e via Wi-Fi, o un
    emulatore): scrivi il seriale di quello giusto, che lo script elenca quando ne trova più di uno. Vuoto: l'unico
    collegato, o quello della variabile ANDROID_SERIAL se è impostata.
.FUNCTIONALITY
    Installa sul telefono
.COMPONENT
    Telefono
.ROLE
    Principale
.EXAMPLE
    .\scripts\install.ps1
.EXAMPLE
    .\scripts\install.ps1 -Release -Serial PZMNS4NBH6UKRGTO
#>
param(
    [switch]$Release,
    [string]$Serial = $env:ANDROID_SERIAL
)

Set-StrictMode -Version Latest
# Non 'Stop': in PowerShell 5.1 lo stderr dei comandi esterni (git, adb) diventerebbe un errore anche quando
# riescono. L'esito di ogni comando esterno si controlla con $LASTEXITCODE.
$ErrorActionPreference = 'Continue'

$script:step = 'avvio'
$script:finished = $false
$script:failed = $false

function Step([string]$name) {
    $script:step = $name
    Write-Host ''
    Write-Host "==> $name" -ForegroundColor Cyan
}

function Fail([string]$reason) {
    $script:failed = $true
    Write-Host ''
    Write-Host "INTERROTTO durante: $script:step" -ForegroundColor Red
    Write-Host "Motivo: $reason" -ForegroundColor Red
    exit 1
}

function Find-Adb {
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $sdks = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)
    if (Test-Path local.properties) {
        $line = Get-Content local.properties | Where-Object { $_ -match '^\s*sdk\.dir\s*=' } | Select-Object -First 1
        # Android Studio scrive il percorso con gli escape dei .properties (C\:\\Users\\...).
        if ($line) { $sdks += ($line -replace '^\s*sdk\.dir\s*=\s*', '' -replace '\\:', ':' -replace '\\\\', '\') }
    }
    foreach ($sdk in $sdks) {
        if (-not $sdk) { continue }
        $path = Join-Path $sdk 'platform-tools\adb.exe'
        if (Test-Path $path) { return $path }
    }
    return $null
}

function Get-Devices([string]$adb) {
    $out = & $adb devices 2>&1 | ForEach-Object { "$_" }
    if ($LASTEXITCODE -ne 0) { Fail "adb devices non risponde: $($out -join ' ')" }
    # Le righe dei dispositivi sono "<seriale><TAB><stato>"; intestazione e messaggi del demone non hanno TAB.
    foreach ($line in $out) {
        if ($line -match '^(\S+)\t(\S+)') { [pscustomobject]@{ Serial = $Matches[1]; State = $Matches[2] } }
    }
}

function Assert-DeviceReady($device) {
    switch ($device.State) {
        'device' { return }
        'unauthorized' { Fail "il telefono $($device.Serial) non ha autorizzato questo PC: sbloccalo e accetta 'Consenti debug USB'." }
        'offline' { Fail "il telefono $($device.Serial) risulta offline: scollega e ricollega il cavo (o riavvia adb con 'adb kill-server')." }
        default { Fail "il telefono $($device.Serial) è nello stato '$($device.State)', non pronto per l'installazione." }
    }
}

$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    # ---- 1. Controlli prima di toccare qualcosa: meglio fermarsi subito che dopo minuti di build ----
    Step 'controlli preliminari'

    if (-not (Get-Command git -ErrorAction SilentlyContinue)) { Fail 'git non è installato o non è nel PATH.' }
    $null = git rev-parse --is-inside-work-tree 2>$null
    if ($LASTEXITCODE -ne 0) { Fail "$root non è un repository git." }
    if (-not (Test-Path gradlew.bat)) { Fail "manca gradlew.bat: generalo una volta con 'gradle wrapper --gradle-version 8.9'." }

    $branch = (git rev-parse --abbrev-ref HEAD 2>$null | Out-String).Trim()
    if ($branch -eq 'HEAD') { Fail "non sei su un branch (HEAD staccato): fai 'git switch dev' e riprova." }
    $upstream = (git rev-parse --abbrev-ref --symbolic-full-name '@{u}' 2>$null | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or -not $upstream) {
        Fail "il branch '$branch' non segue nessun branch remoto: impostalo con 'git branch -u origin/$branch'."
    }

    $adb = Find-Adb
    if (-not $adb) { Fail 'adb non trovato: aggiungi platform-tools dell''Android SDK al PATH o imposta ANDROID_HOME.' }

    $devices = @(Get-Devices $adb)
    if ($Serial) {
        $device = $devices | Where-Object { $_.Serial -eq $Serial } | Select-Object -First 1
        if (-not $device) { Fail "il dispositivo '$Serial' non è collegato (collegati: $(($devices | ForEach-Object { $_.Serial }) -join ', '))." }
    } elseif ($devices.Count -eq 0) {
        Fail 'nessun telefono collegato: collegalo via USB con il debug USB attivo (o collegalo via Wi-Fi con .\scripts\pair-wifi.ps1).'
    } elseif ($devices.Count -gt 1) {
        Fail "sono collegati più dispositivi ($(($devices | ForEach-Object { $_.Serial }) -join ', ')): scegli quale con -Serial <seriale>."
    } else {
        $device = $devices[0]
    }
    Assert-DeviceReady $device
    Write-Host "Branch $branch -> $upstream, dispositivo $($device.Serial)"

    $dirty = @(git status --porcelain)
    if ($dirty.Count -gt 0) {
        Write-Host "Attenzione: $($dirty.Count) file con modifiche non committate. Se il pull li tocca si ferma; altrimenti l'APK le includerà." -ForegroundColor Yellow
    }

    # ---- 2. git fetch ----
    Step 'git fetch'
    git fetch --prune
    if ($LASTEXITCODE -ne 0) { Fail 'git fetch non è riuscito: controlla la connessione e le credenziali di GitHub (messaggio di git qui sopra).' }

    $counts = (git rev-list --left-right --count "HEAD...$upstream" | Out-String).Trim() -split '\s+'
    $ahead, $behind = [int]$counts[0], [int]$counts[1]
    if ($ahead -gt 0 -and $behind -gt 0) {
        Fail "il branch locale ha $ahead commit non pubblicati e $upstream ne ha $behind nuovi: unisci a mano (git pull --rebase o merge) e rilancia."
    }
    if ($behind -eq 0) { Write-Host 'Nessun nuovo commit sul remoto.' }
    else { Write-Host "$behind nuovi commit da scaricare:"; git log --oneline "HEAD..$upstream" }

    # ---- 3. git pull ----
    Step 'git pull'
    git pull --ff-only
    if ($LASTEXITCODE -ne 0) {
        Fail 'git pull non è riuscito, di solito perché i commit in arrivo modificano file che hai cambiato in locale: committali o mettili da parte con git stash (messaggio di git qui sopra).'
    }

    # ---- 4. Build ----
    $variant = if ($Release) { 'Release' } else { 'Debug' }
    Step "build $variant"
    & .\gradlew.bat ":app:assemble$variant"
    if ($LASTEXITCODE -ne 0) { Fail "la build Gradle è fallita (codice $LASTEXITCODE): la causa è qui sopra, sotto 'What went wrong'." }

    $apkDir = "app\build\outputs\apk\$($variant.ToLower())"
    $apk = Get-ChildItem $apkDir -Filter *.apk -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $apk) { Fail "la build è riuscita ma in $apkDir non c'è nessun APK." }
    Write-Host "APK: $($apk.FullName)"

    # ---- 5. Installazione ----
    Step "installazione su $($device.Serial)"
    $state = (& $adb -s $device.Serial get-state 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) { Fail "il telefono $($device.Serial) non è più collegato ($state)." }
    Assert-DeviceReady ([pscustomobject]@{ Serial = $device.Serial; State = $state })

    $out = @(& $adb -s $device.Serial install -r $apk.FullName 2>&1 | ForEach-Object { "$_" })
    $exitCode = $LASTEXITCODE
    $out | ForEach-Object { Write-Host "    $_" }
    $text = $out -join "`n"
    if ($exitCode -ne 0 -or $text -notmatch '(?m)^Success') {
        $code = if ($text -match 'Failure \[(\w+)') { $Matches[1] } else { '' }
        $hint = switch ($code) {
            'INSTALL_FAILED_UPDATE_INCOMPATIBLE' {
                "l'app installata è firmata con un'altra chiave. Disinstallala (adb uninstall com.example.animewatch) e rilancia: " +
                    'liste e progressi sono su Firebase, si perdono solo le modifiche fatte offline e non ancora sincronizzate.'
            }
            'INSTALL_FAILED_VERSION_DOWNGRADE' { "sul telefono c'è una versione più recente di questa (appVersion in gradle.properties)." }
            'INSTALL_FAILED_USER_RESTRICTED' { "il telefono ha bloccato l'installazione: confermala sullo schermo o attiva 'Installa tramite USB' nelle opzioni sviluppatore." }
            'INSTALL_FAILED_INSUFFICIENT_STORAGE' { 'spazio insufficiente sul telefono.' }
            '' { "adb non ha confermato l'installazione (output qui sopra)." }
            default { "adb ha risposto $code (output qui sopra)." }
        }
        Fail $hint
    }

    $script:finished = $true
    Write-Host ''
    Write-Host "Fatto: AnimeWatch ($variant) installata su $($device.Serial)." -ForegroundColor Green
}
catch {
    Fail "errore imprevisto: $($_.Exception.Message)"
}
finally {
    # Ctrl+C non passa da Fail: lo si riconosce qui, perché né il successo né un errore sono stati registrati.
    if (-not $script:finished -and -not $script:failed) {
        Write-Host ''
        Write-Host "INTERROTTO durante: $script:step" -ForegroundColor Red
        Write-Host 'Motivo: interruzione manuale (Ctrl+C) o chiusura del terminale.' -ForegroundColor Red
    }
    Pop-Location
}

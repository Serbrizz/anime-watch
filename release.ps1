<#
.SYNOPSIS
    Pubblica una nuova versione di AnimeWatch: versione, build, commit, tag, push e release su GitHub.

.DESCRIPTION
    Si lancia da main, dopo aver fatto il merge di dev (CLAUDE.md §12).

    1. controlli: branch main pulito e allineato a origin, dev già unito, tag libero,
       configurazione per la release (Gradle :app:verifyReleaseConfig: keystore
       condiviso e google-services.json vero) e GitHub CLI.
       Con gh pronta apre nel Blocco note un file temporaneo vuoto in cui scrivere le
       note di rilascio (compaiono nel popup di aggiornamento dell'app): letto il
       testo, il file si elimina
    2. incrementa appVersion in gradle.properties (unica fonte della versione)
    3. unit test e APK di release firmato (gradlew), copiato in build\release\AnimeWatch.apk;
       controlla con apksigner che sia firmato con il certificato atteso
    4. commit (dopo conferma) "chore: 🔖 release <versione>", tag annotato v<versione>
       e push di main e tag insieme (--atomic: o tutti e due o nessuno)
    5. release su GitHub con gh: titolo "AnimeWatch <versione>", le note e l'APK.
       Senza gh apre la pagina della nuova release, da completare a mano
    6. porta dev allo stesso commit di main (solo se è un avanzamento semplice)

    Se qualcosa fallisce prima del commit, o alla conferma rispondi no, lo script
    rimette gradle.properties e build\release com'erano.

.PARAMETER Bump
    Obbligatorio: patch (1.2.3 -> 1.2.4), minor (1.2.3 -> 1.3.0) o major (1.2.3 -> 2.0.0).
    Accettato anche come -patch / --patch.

.PARAMETER JavaHome
    JDK (17 o successivo) da usare per Gradle al posto di JAVA_HOME.

.PARAMETER Yes
    Non chiede conferme (con -Yes un dev non ancora unito a main interrompe la release).

.EXAMPLE
    .\release.cmd minor

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File release.ps1 patch -JavaHome "C:\Program Files\Java\jdk-17"
#>
param(
    [Parameter(Position = 0)]
    [string]$Bump = '',
    [switch]$Patch,
    [switch]$Minor,
    [switch]$Major,
    [string]$JavaHome = '',
    [switch]$Yes,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$Rest
)

# 'Continue': git e Gradle scrivono su stderr anche messaggi normali. Gli errori veri si
# intercettano con l'exit code.
$ErrorActionPreference = 'Continue'

$root = $PSScriptRoot
$propsFile = Join-Path $root 'gradle.properties'
$releaseBranch = 'main'
$devBranch = 'dev'
$assetName = 'AnimeWatch.apk'
$assetDir = Join-Path $root 'build\release'
$asset = Join-Path $assetDir $assetName
$gradleApk = Join-Path $root 'app\build\outputs\apk\release\app-release.apk'
$gradlew = Join-Path $root 'gradlew.bat'
$utf8 = New-Object System.Text.UTF8Encoding $false
$steps = 6
$width = 68
$clock = [System.Diagnostics.Stopwatch]::StartNew()

# ---- Output ---------------------------------------------------------------------

function Badge([string]$text, [ConsoleColor]$fg, [ConsoleColor]$bg) {
    Write-Host " $text " -ForegroundColor $fg -BackgroundColor $bg -NoNewline
}

function Line([string]$char, [ConsoleColor]$color) { Write-Host ($char * $width) -ForegroundColor $color }

function Step([int]$n, [string]$title) {
    Write-Host ''
    Badge "$n/$steps" Black Cyan
    Write-Host " $title" -ForegroundColor Cyan
    Line '─' DarkGray
}

function Ok([string]$msg) { Write-Host '  ' -NoNewline; Badge 'OK' Black Green; Write-Host " $msg" }
function Warn([string]$msg) { Write-Host '  ' -NoNewline; Badge '!!' Black Yellow; Write-Host " $msg" -ForegroundColor Yellow }
function Detail([string]$msg) { Write-Host "       $msg" -ForegroundColor DarkGray }

function Banner([string]$badge, [ConsoleColor]$fg, [ConsoleColor]$bg, [ConsoleColor]$color, [string]$title) {
    Write-Host ''
    Line '═' $color
    Write-Host '  ' -NoNewline
    Badge $badge $fg $bg
    Write-Host " $title" -ForegroundColor $color
    Line '═' $color
}

function Elapsed {
    $t = $clock.Elapsed
    if ($t.TotalMinutes -ge 1) { return '{0}m {1:00}s' -f [int][math]::Floor($t.TotalMinutes), $t.Seconds }
    return '{0}s' -f [int]$t.TotalSeconds
}

function Ask([string]$question, [string]$choices) {
    Write-Host '  ' -NoNewline
    Badge '?' Black Magenta
    Write-Host " $question " -NoNewline -ForegroundColor White
    Write-Host "[$choices] " -NoNewline -ForegroundColor Yellow
    return ((Read-Host) -match '^(s|si|sì|y|yes)$')
}

function Show-Usage([string]$current) {
    Write-Host ''
    Write-Host '  Uso (da main, dopo il merge di dev)' -ForegroundColor White
    Write-Host '    .\release.cmd ' -NoNewline -ForegroundColor Gray
    Write-Host '<patch|minor|major>' -NoNewline -ForegroundColor Yellow
    Write-Host ' [-Yes] [-JavaHome <jdk>]' -ForegroundColor DarkGray
    if ($current) {
        Write-Host ''
        Write-Host '  Versione attuale: ' -NoNewline -ForegroundColor White
        Write-Host $current -ForegroundColor Cyan
        $choices = @(
            @('patch', 'correzioni di bug'),
            @('minor', 'nuove funzionalità'),
            @('major', 'cambiamenti importanti')
        )
        foreach ($c in $choices) {
            Write-Host ('    {0,-7}' -f $c[0]) -NoNewline -ForegroundColor Yellow
            Write-Host ' -> ' -NoNewline -ForegroundColor DarkGray
            Write-Host ('{0,-9}' -f (Get-NextVersion $current $c[0])) -NoNewline -ForegroundColor Green
            Write-Host $c[1] -ForegroundColor DarkGray
        }
    }
    Write-Host ''
}

function Fail-Usage([string]$msg, [string]$current) {
    Banner 'ERRORE' White DarkRed Red $msg
    Show-Usage $current
    exit 1
}

# ---- Comandi ----------------------------------------------------------------------

function Get-NextVersion([string]$current, [string]$kind) {
    $v = [version]$current
    switch ($kind) {
        'major' { return "$($v.Major + 1).0.0" }
        'minor' { return "$($v.Major).$($v.Minor + 1).0" }
        'patch' { return "$($v.Major).$($v.Minor).$($v.Build + 1)" }
    }
}

# Comando lungo (Gradle): l'output scorre a video.
function Invoke-Checked([string]$what, [scriptblock]$block) {
    & $block
    if ($LASTEXITCODE -ne 0) { throw "$what fallito (exit code $LASTEXITCODE)" }
}

# Comando breve (git): output attenuato e rientrato.
function Invoke-Quiet([string]$what, [scriptblock]$block) {
    $out = & $block 2>&1
    $code = $LASTEXITCODE
    foreach ($l in $out) { if ("$l".Trim()) { Detail "$l" } }
    if ($code -ne 0) { throw "$what fallito (exit code $code)" }
}

# Output di git letto come UTF-8: Windows PowerShell 5.1 userebbe la codepage OEM e
# l'emoji del messaggio di commit apparirebbe illeggibile (solo a video).
function Get-GitOutput([string[]]$gitArgs) {
    $prev = [Console]::OutputEncoding
    try {
        [Console]::OutputEncoding = $utf8
        return (& git @gitArgs)
    } finally {
        [Console]::OutputEncoding = $prev
    }
}

function Get-Prop([string]$text, [string]$name) {
    $m = [regex]::Match($text, "(?m)^$([regex]::Escape($name))=(.*?)\r?$")
    if ($m.Success) { return $m.Groups[1].Value.Trim() }
    return ''
}

# Cartella dell'Android SDK: local.properties (scritto da Android Studio, con \: e \\
# come escape), poi le variabili d'ambiente, poi la cartella predefinita.
function Get-SdkDir {
    $local = Join-Path $root 'local.properties'
    if (Test-Path -LiteralPath $local) {
        $dir = (Get-Prop ([System.IO.File]::ReadAllText($local)) 'sdk.dir') -replace '\\:', ':' -replace '\\\\', '\'
        if ($dir -and (Test-Path -LiteralPath $dir)) { return $dir }
    }
    foreach ($d in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, (Join-Path $env:LOCALAPPDATA 'Android\Sdk'))) {
        if ($d -and (Test-Path -LiteralPath $d)) { return $d }
    }
    throw 'Android SDK non trovato (sdk.dir in local.properties o ANDROID_HOME): serve per controllare la firma.'
}

# SHA-1 (XX:XX:...) del certificato con cui è firmato l'APK, letto con apksigner (con
# minSdk 26 l'APK ha solo le firme v2/v3, che keytool non legge).
function Get-ApkSha1([string]$apk) {
    $sdk = Get-SdkDir
    $buildTools = Get-ChildItem (Join-Path $sdk 'build-tools') -Directory -ErrorAction SilentlyContinue |
        Sort-Object { [version]($_.Name -replace '[^\d.].*$', '') } -Descending | Select-Object -First 1
    if ($null -eq $buildTools) { throw "Android SDK Build-Tools non trovati in $sdk (servono per apksigner)." }
    $out = & (Join-Path $buildTools.FullName 'apksigner.bat') verify --print-certs $apk 2>&1 | ForEach-Object { "$_" } | Out-String
    $m = [regex]::Match($out, 'certificate SHA-1 digest:\s*([0-9a-fA-F]{40})')
    if ($LASTEXITCODE -ne 0 -or -not $m.Success) { throw "Firma di $apk non valida o illeggibile:`n$($out.Trim())" }
    return (($m.Groups[1].Value.ToUpper() -split '(..)' | Where-Object { $_ }) -join ':')
}

# Le note si scrivono solo qui: file temporaneo vuoto aperto nel Blocco note, letto
# quando si preme Invio ed eliminato subito dopo. Il testo resta solo in memoria.
function Read-ReleaseNotes {
    $file = Join-Path ([System.IO.Path]::GetTempPath()) ("AnimeWatch-note-{0}-{1}.txt" -f $Version, [guid]::NewGuid().ToString('N').Substring(0, 8))
    try {
        [System.IO.File]::WriteAllText($file, '', $utf8)
        Start-Process notepad.exe -ArgumentList "`"$file`""
        Write-Host '  ' -NoNewline
        Badge '?' Black Magenta
        Write-Host ' Scrivi le note di rilascio nel Blocco note (compaiono nel popup di aggiornamento),' -ForegroundColor White
        Write-Host '      salva e premi Invio qui per continuare ' -NoNewline -ForegroundColor White
        Read-Host | Out-Null
        return [System.IO.File]::ReadAllText($file).Trim()
    } finally {
        Remove-Item -LiteralPath $file -Force -ErrorAction SilentlyContinue
    }
}

# ---- Stato, per tornare indietro se qualcosa va storto -----------------------------

$versionWritten = $false
$assetBackup = ''
$assetTouched = $false
$committed = $false
$tagged = $false
$pushed = $false
$ghReady = $false
$notesText = ''

# Annulla quello che lo script ha fatto prima del commit; dopo il commit non tocca nulla.
function Undo-Local {
    if ($committed -or $pushed) { return }
    if ($assetTouched) {
        if ($assetBackup -and (Test-Path -LiteralPath $assetBackup)) {
            Move-Item -LiteralPath $assetBackup -Destination $asset -Force
        } elseif (Test-Path -LiteralPath $asset) {
            Remove-Item -LiteralPath $asset -Force
        }
        $script:assetTouched = $false
        Ok "build\release\$assetName ripristinato a quello di prima"
    }
    if ($versionWritten) {
        [System.IO.File]::WriteAllText($propsFile, $props, $utf8)
        $script:versionWritten = $false
        Ok "Versione in gradle.properties ripristinata a $current"
    }
}

function Show-Recovery {
    $hints = @()
    if ($tagged) {
        $hints += "Il tag $tag esiste solo in locale. Riprova il push:  git push --atomic origin HEAD $tag"
        $hints += "oppure eliminalo:  git tag -d $tag"
    }
    if ($committed) {
        $hints += 'Il commit di release esiste solo in locale. Per annullarlo:  git reset --hard HEAD~1'
    }
    Write-Host ''
    if ($hints.Count -eq 0) {
        Write-Host "  Il repository è com'era prima della release." -ForegroundColor Gray
        return
    }
    Write-Host '  Da sistemare a mano' -ForegroundColor White
    foreach ($h in $hints) { Write-Host "    - $h" -ForegroundColor Yellow }
}

# ---- Argomenti --------------------------------------------------------------------

$props = [System.IO.File]::ReadAllText($propsFile, $utf8)
$current = Get-Prop $props 'appVersion'
$repoSlug = Get-Prop $props 'appReleaseRepo'
$signingSha1 = (Get-Prop $props 'appSigningSha1').ToUpper()
if ($current -notmatch '^\d+\.\d+\.\d+$') {
    Banner 'ERRORE' White DarkRed Red 'appVersion non trovata in gradle.properties (appVersion=X.Y.Z).'
    exit 1
}
if (-not $repoSlug -or -not $signingSha1) {
    Banner 'ERRORE' White DarkRed Red 'Mancano appReleaseRepo o appSigningSha1 in gradle.properties.'
    exit 1
}
$repoUrl = "https://github.com/$repoSlug"

$requested = @()
if ($Bump) { $requested += $Bump }
if ($Patch) { $requested += 'patch' }
if ($Minor) { $requested += 'minor' }
if ($Major) { $requested += 'major' }
if ($Rest) { $requested += $Rest }
$requested = @($requested | Where-Object { "$_".Trim() } | ForEach-Object { "$_".Trim() })
$validKinds = @('patch', 'minor', 'major')
$kinds = @($requested | ForEach-Object { $_.TrimStart('-').ToLower() } | Select-Object -Unique)
if ($kinds.Count -eq 0) { Fail-Usage 'Manca il tipo di release: indica patch, minor o major.' $current }
if ($kinds.Count -gt 1) { Fail-Usage ("Indica un solo tipo di release (ricevuti: {0})." -f ($requested -join ', ')) $current }
$kind = $kinds[0]
if ($kind -notin $validKinds) { Fail-Usage "Tipo di release non valido: '$($requested[0])'. Usa patch, minor o major." $current }

$Version = Get-NextVersion $current $kind
$parts = $Version.Split('.')
if ([int]$parts[1] -ge 100 -or [int]$parts[2] -ge 100) {
    Fail-Usage "La versione $Version non è valida: minor e patch devono restare sotto 100 (versionCode)." $current
}
$tag = "v$Version"
$message = "chore: 🔖 release $Version"

# ---- Release --------------------------------------------------------------------

Push-Location $root
try {
    $branch = (git symbolic-ref --short -q HEAD)
    if ($LASTEXITCODE -ne 0 -or -not $branch) { $branch = '' }

    Write-Host ''
    Line '═' Cyan
    Write-Host '  AnimeWatch' -NoNewline -ForegroundColor White
    Write-Host '  release ' -NoNewline -ForegroundColor DarkGray
    Write-Host $kind -ForegroundColor Yellow
    Write-Host '  ' -NoNewline
    Write-Host $current -NoNewline -ForegroundColor Gray
    Write-Host '  ->  ' -NoNewline -ForegroundColor DarkGray
    Write-Host $Version -NoNewline -ForegroundColor Green
    Write-Host "     tag $tag · $repoSlug" -ForegroundColor DarkGray
    Line '═' Cyan

    # ---- 1. Controlli -------------------------------------------------------------

    Step 1 'Controlli'
    if ($branch -ne $releaseBranch) {
        throw "Le release partono da ${releaseBranch} (sei su '$branch'): git checkout $releaseBranch; git merge $devBranch"
    }
    Ok "Branch $releaseBranch"

    $dirty = @(git status --porcelain)
    if ($dirty.Count -gt 0) {
        foreach ($d in $dirty) { Detail $d }
        throw 'Ci sono modifiche non committate: le modifiche passano da dev, su main si fa solo il merge.'
    }
    Ok 'Working tree pulito'

    Detail 'Aggiornamento da origin...'
    git fetch --quiet origin 2>$null
    if ($LASTEXITCODE -ne 0) { throw 'Impossibile contattare origin: sei connesso a Internet?' }
    $behind = [int](git rev-list --count "HEAD..origin/$releaseBranch")
    if ($behind -gt 0) { throw "$releaseBranch è indietro di $behind commit rispetto a origin: git pull" }
    $ahead = [int](git rev-list --count "origin/$releaseBranch..HEAD")
    if ($ahead -gt 0) { Ok "$releaseBranch allineato a origin ($ahead commit locali da pubblicare con la release)" }
    else { Ok "$releaseBranch allineato a origin" }

    git rev-parse -q --verify "refs/remotes/origin/$devBranch" *> $null
    if ($LASTEXITCODE -eq 0) {
        $missing = @(Get-GitOutput @('log', '--oneline', "HEAD..origin/$devBranch"))
        if ($missing.Count -gt 0) {
            Warn "$($missing.Count) commit di origin/$devBranch non sono in ${releaseBranch}:"
            foreach ($l in $missing) { Detail $l }
            if ($Yes) { throw "Fai prima il merge di ${devBranch}: git merge origin/$devBranch" }
            if (-not (Ask "Pubblicare senza quei commit?" 's = continua / N = annulla')) {
                Banner 'ANNULLATO' Black Yellow Yellow "Nessuna modifica. Fai il merge: git merge origin/$devBranch"
                Write-Host ''
                exit 1
            }
        } else {
            Ok "$devBranch già unito a $releaseBranch"
        }
    }

    git rev-parse -q --verify "refs/tags/$tag" *> $null
    $localTag = $LASTEXITCODE -eq 0
    $remoteTag = git ls-remote --tags origin "refs/tags/$tag"
    if ($localTag -or $remoteTag) {
        throw "Il tag $tag esiste già (in locale o su origin). Se è di una release annullata eliminalo: git tag -d $tag; git push origin :refs/tags/$tag"
    }
    Ok "Tag $tag libero"

    if ($JavaHome) { $env:JAVA_HOME = $JavaHome }
    Detail 'Controllo della configurazione per la release (Gradle)...'
    & $gradlew -q :app:verifyReleaseConfig
    if ($LASTEXITCODE -ne 0) { throw 'Configurazione per la release incompleta: vedi l''errore di Gradle qui sopra.' }
    Ok 'Keystore condiviso e google-services.json'

    if (Get-Command gh -ErrorAction SilentlyContinue) {
        gh auth status --hostname github.com *> $null
        $ghReady = $LASTEXITCODE -eq 0
        if (-not $ghReady) { Warn 'GitHub CLI non autenticata (gh auth login): la release su GitHub sarà da creare a mano' }
    } else {
        Warn 'GitHub CLI non trovata (winget install GitHub.cli): la release su GitHub sarà da creare a mano'
    }
    if ($ghReady) {
        $notesText = Read-ReleaseNotes
        if (-not $notesText) { throw 'Note di rilascio vuote: scrivile nel Blocco note e salva prima di premere Invio.' }
        Ok ("Note di rilascio scritte ({0} righe)" -f @($notesText -split "`r?`n").Count)
    }

    # ---- 2. Versione --------------------------------------------------------------

    Step 2 'Versione'
    $updated = [regex]::Replace($props, '(?m)^appVersion=\d+\.\d+\.\d+', "appVersion=$Version")
    [System.IO.File]::WriteAllText($propsFile, $updated, $utf8)
    $versionWritten = $true
    Ok "gradle.properties  $current -> $Version"

    # ---- 3. Build -----------------------------------------------------------------

    Step 3 'Test e APK di release (gradlew)'
    New-Item -ItemType Directory -Force -Path $assetDir | Out-Null
    if (Test-Path -LiteralPath $asset) {
        $assetBackup = "$asset.bak"
        Move-Item -LiteralPath $asset -Destination $assetBackup -Force
    }
    $assetTouched = $true
    Invoke-Checked 'Gradle' { & $gradlew :app:testDebugUnitTest :app:assembleRelease }
    Copy-Item -LiteralPath $gradleApk -Destination $asset -Force
    $sha1 = Get-ApkSha1 $asset
    if ($sha1 -ne $signingSha1) {
        throw "L'APK è firmato con SHA-1 $sha1, atteso ${signingSha1}: chi ha l'app non potrebbe aggiornarla."
    }
    Ok ('build\release\{0}  ({1:N1} MB), firmato con il certificato atteso' -f $assetName, ((Get-Item -LiteralPath $asset).Length / 1MB))

    # ---- 4. Commit, tag, push -----------------------------------------------------

    Step 4 'Commit, tag e push'
    Write-Host '  Messaggio      ' -NoNewline -ForegroundColor White
    Write-Host $message -ForegroundColor Green
    Write-Host '  Tag            ' -NoNewline -ForegroundColor White
    Write-Host $tag -ForegroundColor Green
    Write-Host '  Push su        ' -NoNewline -ForegroundColor White
    Write-Host "origin/$releaseBranch" -ForegroundColor Green
    Write-Host '  Release GitHub ' -NoNewline -ForegroundColor White
    if ($ghReady) { Write-Host "AnimeWatch $Version su $repoSlug, con $assetName" -ForegroundColor Green }
    else { Write-Host 'da creare a mano nel browser' -ForegroundColor Yellow }

    if (-not $Yes) {
        Write-Host ''
        $what = if ($ghReady) { 'commit, tag, push e release su GitHub' } else { 'commit, tag e push' }
        if (-not (Ask "Procedere con ${what}?" 's/N')) {
            Banner 'ANNULLATO' Black Yellow Yellow 'Nessun commit, tag o push è stato fatto.'
            Undo-Local
            Show-Recovery
            Write-Host ''
            exit 1
        }
        Write-Host ''
    }

    Invoke-Quiet 'git add' { git -c core.safecrlf=false add gradle.properties }
    # Il messaggio passa da un file UTF-8: così l'emoji arriva intatta a git anche da PowerShell 5.1.
    $msgFile = [System.IO.Path]::GetTempFileName()
    try {
        [System.IO.File]::WriteAllText($msgFile, $message, $utf8)
        Invoke-Quiet 'git commit' { git commit -q -F $msgFile }
    } finally {
        Remove-Item $msgFile -ErrorAction SilentlyContinue
    }
    $committed = $true
    Ok ("Commit {0}" -f (Get-GitOutput @('log', '--oneline', '-1')))

    Invoke-Quiet 'git tag' { git tag -a $tag -m "AnimeWatch $Version" }
    $tagged = $true
    Ok "Tag $tag"

    Detail "Push su origin ($releaseBranch + $tag)..."
    Invoke-Quiet 'git push' { git push --atomic origin HEAD $tag }
    $pushed = $true
    $tagged = $false
    $committed = $false
    if ($assetBackup) { Remove-Item -LiteralPath $assetBackup -Force -ErrorAction SilentlyContinue }
    Ok "Push di $releaseBranch e $tag su origin"

    # ---- 5. Release su GitHub -----------------------------------------------------

    Step 5 'Release su GitHub'
    $published = $false
    if ($ghReady) {
        Detail "Creazione della release e caricamento di $assetName..."
        # Le note passano da un file: come argomento PowerShell 5.1 rovinerebbe virgolette e a capo.
        $ghNotes = [System.IO.Path]::GetTempFileName()
        try {
            [System.IO.File]::WriteAllText($ghNotes, $notesText, $utf8)
            gh release create $tag $asset --repo $repoSlug --title "AnimeWatch $Version" --notes-file $ghNotes --verify-tag
            $ghCode = $LASTEXITCODE
        } finally {
            Remove-Item -LiteralPath $ghNotes -Force -ErrorAction SilentlyContinue
        }
        if ($ghCode -eq 0) {
            $published = $true
            Ok "Release pubblicata: $repoUrl/releases/tag/$tag"
        } else {
            Warn "gh release create fallito (exit code $ghCode)"
            Detail "Creala a mano da $repoUrl/releases/new?tag=$tag allegando $asset"
            Detail 'con queste note (copiale da qui, non sono salvate altrove):'
            Write-Host ''
            foreach ($l in ($notesText -split "`r?`n")) { Write-Host "    $l" -ForegroundColor Cyan }
        }
    } else {
        $releaseUrl = "$repoUrl/releases/new?tag=$tag&title=AnimeWatch%20$Version"
        Write-Host '  Nella pagina aperta nel browser:' -ForegroundColor White
        Write-Host "    1. allega $asset (con il nome esatto $assetName)" -ForegroundColor Gray
        Write-Host '    2. scrivi le novità nella descrizione (compaiono nel popup di aggiornamento)' -ForegroundColor Gray
        Write-Host '    3. lascia spento "Set as a pre-release" e premi "Publish release"' -ForegroundColor Gray
        Start-Process $releaseUrl
    }

    # ---- 6. dev -------------------------------------------------------------------

    Step 6 "Allineamento di $devBranch"
    # Dopo il merge di dev, main ha in più solo il commit di release: dev avanza fino a lì, così
    # chi lavora su dev compila già con la nuova versione.
    git rev-parse -q --verify "refs/remotes/origin/$devBranch" *> $null
    if ($LASTEXITCODE -eq 0) {
        git merge-base --is-ancestor "origin/$devBranch" HEAD
        if ($LASTEXITCODE -eq 0) {
            git push --quiet origin "HEAD:refs/heads/$devBranch" 2>$null
            if ($LASTEXITCODE -eq 0) { Ok "origin/$devBranch portato alla release" }
            else { Warn "Push di $devBranch fallito: git push origin ${releaseBranch}:$devBranch" }
        } else {
            Warn "origin/$devBranch ha commit nuovi: unisci la release a mano (git checkout $devBranch; git merge $releaseBranch)"
        }
    }
    git rev-parse -q --verify "refs/heads/$devBranch" *> $null
    if ($LASTEXITCODE -eq 0) {
        git merge-base --is-ancestor $devBranch HEAD
        if ($LASTEXITCODE -eq 0) {
            git branch -f $devBranch HEAD
            Ok "$devBranch locale portato alla release"
        } else {
            Warn "$devBranch locale ha commit non in ${releaseBranch}: git checkout $devBranch; git merge $releaseBranch"
        }
    }

    if ($published) {
        Banner 'FATTO' Black Green Green "AnimeWatch $Version pubblicata  ($(Elapsed))"
    } else {
        Banner 'FATTO' Black Green Green "AnimeWatch ${Version}: tag $tag su origin  ($(Elapsed))"
        Write-Host "  Manca solo pubblicare la release su GitHub con $assetName allegato." -ForegroundColor Gray
    }
    Write-Host ''
} catch {
    Banner 'ERRORE' White DarkRed Red "Release interrotta: $($_.Exception.Message)"
    Undo-Local
    Show-Recovery
    Write-Host ''
    exit 1
} finally {
    Pop-Location
}

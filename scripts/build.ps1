<#
.SYNOPSIS
    Compila l'APK dell'app senza installarlo e apre la cartella dove si trova.
.DESCRIPTION
    Utile per passare l'app a qualcuno o installarla a mano. Per compilare e installare sul telefono collegato in un
    passo c'è "Installa sul telefono"; per pubblicare una versione c'è "Pubblica una release".

    Compila il codice così com'è sul PC, senza scaricare aggiornamenti da GitHub.

    L'APK è firmato con il keystore condiviso, quindi si installa anche sopra le versioni pubblicate.

    Serve: keystore.properties che punta al keystore condiviso; per la variante release anche il vero
    google-services.json.
.NOTES
    1. Scegli se compilare la variante debug (predefinita) o release.
    2. Premi Esegui e aspetta la scritta verde FATTO: da uno a qualche minuto.
    3. Si apre la cartella con il file .apk.
.PARAMETER Release
    APK di release
    Acceso compila la variante release, quella che ricevono gli utenti (serve il vero google-services.json). Spento
    compila la debug.
.FUNCTIONALITY
    Compila l'APK
.COMPONENT
    App
.EXAMPLE
    .\scripts\build.ps1 -Release
#>
param(
    [switch]$Release
)
$root = Split-Path -Parent $PSScriptRoot
$variant = if ($Release) { 'Release' } else { 'Debug' }
Push-Location $root
try {
    & .\gradlew.bat ":app:assemble$variant"
    $code = $LASTEXITCODE
    if ($code -eq 0) {
        $apkDir = Join-Path $root "app\build\outputs\apk\$($variant.ToLower())"
        Write-Host ''
        Write-Host "Il file .apk è in $apkDir" -ForegroundColor Green
        Start-Process explorer.exe -ArgumentList ('"{0}"' -f $apkDir)
    }
    exit $code
} finally {
    Pop-Location
}

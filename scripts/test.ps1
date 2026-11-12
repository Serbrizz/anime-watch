<#
.SYNOPSIS
    Lancia gli unit test dell'app (Gradle :app:testDebugUnitTest).
.DESCRIPTION
    Esegue sul PC, senza telefono, i test automatici della logica dell'app: motore dei consigliati, regole delle liste
    condivise, raggruppamento delle stagioni in franchise, abbinamento delle puntate di TMDB, confronto delle versioni
    per gli aggiornamenti e classificazione degli errori.

    Conviene lanciarli prima di un commit o di una release (la release li lancia comunque da sola).

    La prima esecuzione può richiedere qualche minuto, perché Gradle scarica le dipendenze e compila; le successive
    sono più veloci.

    Se un test fallisce, l'output mostra quale e indica il report HTML con tutti i dettagli
    (app\build\reports\tests\testDebugUnitTest\index.html).
.NOTES
    1. Premi Esegui.
    2. Aspetta la fine: FATTO in verde vuol dire che tutti i test passano.
    3. Con ERRORE in rosso, cerca nell'output le righe FAILED, oppure apri il report indicato in fondo.
.FUNCTIONALITY
    Unit test
.COMPONENT
    App
.EXAMPLE
    .\scripts\test.ps1
#>
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    & .\gradlew.bat :app:testDebugUnitTest
    $code = $LASTEXITCODE
    if ($code -ne 0) {
        Write-Host ''
        Write-Host 'Report: app\build\reports\tests\testDebugUnitTest\index.html' -ForegroundColor Yellow
    }
    exit $code
} finally {
    Pop-Location
}

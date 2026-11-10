# Rigenera la chiave di debug di questo PC e stampa lo SHA-1 da registrare su Firebase.
# La vecchia chiave resta come debug.keystore.bak.
#
# Uso: powershell -ExecutionPolicy Bypass -File scripts\new-debug-key.ps1

$ErrorActionPreference = "Stop"
$keystore = "$env:USERPROFILE\.android\debug.keystore"
$keytool = if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\keytool.exe")) { "$env:JAVA_HOME\bin\keytool.exe" } else { "keytool" }

if (Test-Path $keystore) { Move-Item $keystore "$keystore.bak" -Force }
New-Item -ItemType Directory -Force (Split-Path $keystore) | Out-Null

# Stessi parametri della chiave che genera Android Studio.
& $keytool -genkeypair -keystore $keystore -alias androiddebugkey -storepass android -keypass android `
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"

& $keytool -list -v -keystore $keystore -alias androiddebugkey -storepass android | Select-String "SHA1:"

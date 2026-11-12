<#
.SYNOPSIS
    Collega questo PC all'account Cloudflare del proxy (wrangler login).
.DESCRIPTION
    Serve una volta per PC, prima di "Deploy del proxy", "Log del proxy" e "Imposta una chiave del proxy": wrangler
    salva l'accesso sul PC e lo riusa le volte dopo, finché non scade.

    Apre il browser sulla pagina di Cloudflare: lì accedi con l'account che gestisce il proxy (o a cui sei stato
    invitato come membro) e autorizzi wrangler. Finché non autorizzi, lo script resta in attesa.

    Alla fine mostra con quale account sei collegato (wrangler whoami).

    Serve: Node.js. La prima volta installa wrangler (npm install).
.NOTES
    1. Premi Esegui: si apre il browser.
    2. Accedi a Cloudflare e premi "Allow" per autorizzare wrangler.
    3. Torna qui: l'output dice "Successfully logged in" e mostra email e account.
    4. Aspetta la scritta verde FATTO.
.FUNCTIONALITY
    Accedi a Cloudflare
.COMPONENT
    Proxy
.EXAMPLE
    .\scripts\proxy-login.ps1
#>
Push-Location (Join-Path (Split-Path -Parent $PSScriptRoot) 'proxy')
try {
    if (-not (Test-Path node_modules)) {
        & npm.cmd install
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }
    & npx.cmd wrangler login
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & npx.cmd wrangler whoami
    exit $LASTEXITCODE
} finally {
    Pop-Location
}

<#
.SYNOPSIS
    Finestra con gli script del progetto: li elenca, ne mostra le opzioni e li lancia, con l'output al suo interno.
.DESCRIPTION
    Elenca ogni scripts\*.ps1 tranne questo. Tutto quello che mostra viene dallo script stesso, quindi per aggiungere
    un comando basta un nuovo .ps1 in scripts\:
    - titolo: .FUNCTIONALITY (senza, il nome del file);
    - descrizione: .SYNOPSIS (una frase) e .DESCRIPTION ("Cosa fa") del commento di aiuto iniziale;
    - istruzioni: .NOTES ("Come si usa"), un passo per riga numerata ("1. ...");
    - gruppo: .COMPONENT (senza, finisce in "Altro");
    - opzioni: i parametri di param(). [switch] diventa un interruttore, [ValidateSet(...)] una fila di pulsanti, il
      resto un campo di testo. Di .PARAMETER la prima riga è il nome mostrato, il resto la spiegazione (con una riga
      sola, il nome è quello del parametro); [Parameter(DontShow)] nasconde un parametro.

    Gli script girano dentro la finestra: un powershell nascosto con input e output redirezionati, l'output nel
    pannello in basso e un campo per rispondere quando lo script chiede qualcosa (Read-Host). "In una console" li
    apre invece in una finestra PowerShell separata.
.EXAMPLE
    .\gui.cmd
#>
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName PresentationFramework, PresentationCore, WindowsBase
# AwNative: barra del titolo scura (Windows 10 20H1+) e console in primo piano.
# AwRunner: avvia un processo con input/output redirezionati e ne raccoglie l'output su thread separati (anche le
# righe senza a capo, come i prompt di Read-Host); la finestra lo svuota con un timer.
Add-Type -TypeDefinition @'
using System;
using System.Collections.Concurrent;
using System.Diagnostics;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;

public static class AwNative {
    [DllImport("dwmapi.dll")] static extern int DwmSetWindowAttribute(IntPtr hwnd, int attr, ref int value, int size);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hwnd);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hwnd, int cmd);
    public static void DarkTitleBar(IntPtr hwnd) {
        int on = 1;
        if (DwmSetWindowAttribute(hwnd, 20, ref on, 4) != 0) DwmSetWindowAttribute(hwnd, 19, ref on, 4);
    }
}

public sealed class AwRunner {
    readonly Process proc;
    readonly StreamWriter input;
    readonly ConcurrentQueue<string> queue = new ConcurrentQueue<string>();

    public AwRunner(string file, string arguments, string workDir) {
        var psi = new ProcessStartInfo(file, arguments);
        psi.UseShellExecute = false;
        psi.CreateNoWindow = true;
        psi.WorkingDirectory = workDir;
        psi.RedirectStandardInput = true;
        psi.RedirectStandardOutput = true;
        psi.RedirectStandardError = true;
        psi.StandardOutputEncoding = new UTF8Encoding(false);
        psi.StandardErrorEncoding = new UTF8Encoding(false);
        proc = Process.Start(psi);
        // Senza BOM: lo StreamWriter predefinito ne mette uno davanti alla prima risposta.
        input = new StreamWriter(proc.StandardInput.BaseStream, new UTF8Encoding(false));
        input.AutoFlush = true;
        Pump(proc.StandardOutput);
        Pump(proc.StandardError);
    }

    void Pump(StreamReader reader) {
        var thread = new Thread(() => {
            var buffer = new char[4096];
            try {
                int n;
                while ((n = reader.Read(buffer, 0, buffer.Length)) > 0) queue.Enqueue(new string(buffer, 0, n));
            } catch { }
        });
        thread.IsBackground = true;
        thread.Start();
    }

    public int Id { get { return proc.Id; } }
    public bool HasExited { get { return proc.HasExited; } }
    public int ExitCode { get { return proc.ExitCode; } }

    public string Drain() {
        var sb = new StringBuilder();
        string chunk;
        while (queue.TryDequeue(out chunk)) sb.Append(chunk);
        return sb.ToString();
    }

    public void Send(string line) {
        try { input.WriteLine(line); } catch { }
    }
}
'@

$selfPath = $PSCommandPath
$scriptsDir = $PSScriptRoot
$root = Split-Path -Parent $scriptsDir
# Gli script di avvio (parametri + chiamata) si scrivono qui e si cancellano a fine esecuzione.
$tempDir = Join-Path ([IO.Path]::GetTempPath()) 'animewatch-scripts'
# Font della finestra (l'output resta in Consolas), scelto con il pulsante in basso a sinistra e ricordato per utente.
# M PLUS Rounded 1c e Nunito stanno in scripts\fonts (licenza OFL, ridotti ai caratteri che servono: i caratteri
# mancanti arrivano da Yu Gothic UI), quindi non vanno installati; Bahnschrift e Yu Gothic UI sono di Windows.
$settingsFile = Join-Path $env:APPDATA 'animewatch-scripts\gui.json'
$fontBase = [Uri]::new((Join-Path $scriptsDir 'fonts') + '\')
$uiFonts = [ordered]@{
    'M PLUS Rounded 1c' = [Windows.Media.FontFamily]::new($fontBase, './#Rounded Mplus 1c, Yu Gothic UI, Segoe UI')
    'Nunito' = [Windows.Media.FontFamily]::new($fontBase, './#Nunito, Yu Gothic UI, Segoe UI')
    'Bahnschrift' = [Windows.Media.FontFamily]::new('Bahnschrift, Yu Gothic UI, Segoe UI')
    'Yu Gothic UI' = [Windows.Media.FontFamily]::new('Yu Gothic UI, Segoe UI')
}
$otherGroup = 'Altro'
# Ordine dei gruppi nell'elenco; quelli che non ci sono vanno dopo, in ordine alfabetico, e "Altro" in fondo.
$groupOrder = 'App', 'Telefono', 'Firebase', 'Release', 'Proxy'
$helpKeywords = 'SYNOPSIS|DESCRIPTION|PARAMETER|EXAMPLE|INPUTS|OUTPUTS|NOTES|LINK|COMPONENT|ROLE|FUNCTIONALITY'
# Icone dei gruppi (Segoe MDL2 Assets); un gruppo nuovo prende quella generica.
$groupIcons = @{
    'Telefono' = [char]0xE8EA; 'App' = [char]0xE943; 'Firebase' = [char]0xE753
    'Proxy' = [char]0xE968; 'Release' = [char]0xE898
}
$defaultIcon = [char]0xE756
$ansi = [regex]'\x1B\[[0-9;?]*[ -/]*[@-~]|\x1B\][^\x07]*\x07'
$maxLines = 5000

$script:scripts = @()
$script:current = $null
$script:commandText = ''
$script:values = @{}
$script:runs = New-Object System.Collections.Generic.List[object]
$script:selectedRun = $null

# ---- Lettura degli script ------------------------------------------------------------------------------------------

# Toglie il rientro comune e le righe vuote ai bordi; i rientri relativi (elenchi) restano.
function Format-Block([string[]]$lines) {
    $indents = @($lines | Where-Object { $_.Trim() } | ForEach-Object { $_.Length - $_.TrimStart().Length })
    $cut = 0
    if ($indents.Count) { $cut = ($indents | Measure-Object -Minimum).Minimum }
    $out = foreach ($l in $lines) { if ($l.Length -ge $cut) { $l.Substring($cut).TrimEnd() } else { $l.Trim() } }
    return (@($out) -join "`n").Trim("`n")
}

function Read-Help([string]$text) {
    $help = @{ Title = ''; Synopsis = ''; Description = ''; Steps = ''; Component = ''; Params = @{} }
    $m = [regex]::Match($text, '(?s)<#(.*?)#>')
    if (-not $m.Success) { return $help }
    $sections = New-Object System.Collections.Generic.List[object]
    $section = $null
    foreach ($line in ($m.Groups[1].Value -split '\r?\n')) {
        if ($line -match "^\s*\.($helpKeywords)\b\s*(.*)$") {
            $section = @{ Key = $Matches[1].ToUpper(); Arg = $Matches[2].Trim(); Lines = New-Object System.Collections.Generic.List[string] }
            $sections.Add($section)
        } elseif ($section) {
            $section.Lines.Add($line)
        }
    }
    foreach ($s in $sections) {
        $body = Format-Block $s.Lines.ToArray()
        switch ($s.Key) {
            'SYNOPSIS' { $help.Synopsis = $body }
            'DESCRIPTION' { $help.Description = $body }
            'NOTES' { $help.Steps = $body }
            'COMPONENT' { $help.Component = ($body -split "`n")[0].Trim() }
            'FUNCTIONALITY' { $help.Title = ($body -split "`n")[0].Trim() }
            'PARAMETER' { $help.Params[$s.Arg.ToLower()] = $body }
        }
    }
    return $help
}

# I parametri si leggono dall'AST, senza eseguire lo script.
function Get-ScriptParams([string]$path, $help) {
    $tokens = $null
    $errors = $null
    $ast = [Management.Automation.Language.Parser]::ParseFile($path, [ref]$tokens, [ref]$errors)
    if (-not $ast.ParamBlock) { return }
    foreach ($p in $ast.ParamBlock.Parameters) {
        $name = $p.Name.VariablePath.UserPath
        $hidden = $false
        $mandatory = $false
        $choices = @()
        foreach ($a in $p.Attributes) {
            if ($a -isnot [Management.Automation.Language.AttributeAst]) { continue }
            switch ($a.TypeName.Name) {
                'Parameter' {
                    foreach ($na in $a.NamedArguments) {
                        $on = $na.ExpressionOmitted -or $na.Argument.Extent.Text -eq '$true'
                        if ($na.ArgumentName -eq 'DontShow' -and $on) { $hidden = $true }
                        if ($na.ArgumentName -eq 'Mandatory' -and $on) { $mandatory = $true }
                    }
                }
                'ValidateSet' { $choices = @($a.PositionalArguments | ForEach-Object { "$($_.SafeGetValue())" }) }
            }
        }
        if ($hidden) { continue }

        $kind = 'text'
        if ($p.StaticType -eq [switch]) { $kind = 'switch' } elseif ($choices.Count) { $kind = 'choice' }
        $default = ''
        if ($p.DefaultValue) {
            if ($p.DefaultValue -is [Management.Automation.Language.StringConstantExpressionAst]) {
                $default = $p.DefaultValue.Value
            } else {
                $default = $p.DefaultValue.Extent.Text
            }
        }
        $label = $name
        $paramHelp = ''
        if ($help.Params.ContainsKey($name.ToLower())) {
            $lines = @($help.Params[$name.ToLower()] -split "`n")
            if ($lines.Count -gt 1) {
                $label = $lines[0].Trim()
                $paramHelp = ($lines[1..($lines.Count - 1)] -join "`n").Trim()
            } else {
                $paramHelp = $lines[0]
            }
        }
        [pscustomobject]@{
            Name = $name
            Label = $label
            Kind = $kind
            Choices = $choices
            Default = $default
            Mandatory = $mandatory
            IsArray = $p.StaticType.IsArray
            Help = $paramHelp
        }
    }
}

function Get-Scripts {
    $found = foreach ($file in (Get-ChildItem -LiteralPath $scriptsDir -Filter *.ps1 -File)) {
        if ($file.FullName -eq $selfPath) { continue }
        try {
            $help = Read-Help ([IO.File]::ReadAllText($file.FullName))
            $group = $otherGroup
            if ($help.Component) { $group = $help.Component }
            $title = $file.BaseName
            if ($help.Title) { $title = $help.Title }
            [pscustomobject]@{
                Name = $file.BaseName
                Title = $title
                Path = $file.FullName
                Group = $group
                Synopsis = $help.Synopsis
                Description = $help.Description
                Steps = $help.Steps
                Params = @(Get-ScriptParams $file.FullName $help)
            }
        } catch {
            [pscustomobject]@{
                Name = $file.BaseName; Title = $file.BaseName; Path = $file.FullName; Group = $otherGroup
                Synopsis = "Non leggibile: $($_.Exception.Message)"; Description = ''; Steps = ''; Params = @()
            }
        }
    }
    $rank = {
        $i = [array]::IndexOf($groupOrder, $_.Group)
        if ($i -ge 0) { $i } elseif ($_.Group -eq $otherGroup) { [int]::MaxValue } else { $groupOrder.Count }
    }
    return @($found | Sort-Object @{ Expression = $rank }, Group, Title)
}

# ---- Comando ---------------------------------------------------------------------------------------------------------

function Get-Values($s) {
    if (-not $script:values.ContainsKey($s.Path)) {
        $v = @{}
        foreach ($p in $s.Params) {
            switch ($p.Kind) {
                'switch' { $v[$p.Name] = $false }
                'choice' { $v[$p.Name] = $(if ($p.Default -in $p.Choices) { $p.Default } else { '' }) }
                default { $v[$p.Name] = '' }
            }
        }
        $script:values[$s.Path] = $v
    }
    return $script:values[$s.Path]
}

# Gli argomenti da passare: solo quelli scelti, il resto lo decide il valore predefinito dello script.
function Get-Arguments($s) {
    $vals = Get-Values $s
    foreach ($p in $s.Params) {
        $v = $vals[$p.Name]
        if ($p.Kind -eq 'switch') {
            if ($v) { [pscustomobject]@{ Name = $p.Name; Value = $true } }
            continue
        }
        $text = "$v".Trim()
        if (-not $text) { continue }
        if ($p.IsArray) {
            [pscustomobject]@{ Name = $p.Name; Value = @($text -split '\s*,\s*' | Where-Object { $_ }) }
        } else {
            [pscustomobject]@{ Name = $p.Name; Value = $text }
        }
    }
}

function ConvertTo-Literal([string]$s) { "'" + $s.Replace("'", "''") + "'" }

function Format-Value([string]$s) {
    if ($s -match '^[\w.:\\/@+-]+$') { return $s }
    return ConvertTo-Literal $s
}

function Format-Command($s, $arguments) {
    $parts = @(".\scripts\$($s.Name).ps1")
    foreach ($a in $arguments) {
        if ($a.Value -is [bool]) { $parts += "-$($a.Name)" }
        elseif ($a.Value -is [array]) { $parts += "-$($a.Name) " + (($a.Value | ForEach-Object { Format-Value $_ }) -join ',') }
        else { $parts += "-$($a.Name) $(Format-Value $a.Value)" }
    }
    return $parts -join ' '
}

function Test-Mandatory($s) {
    $vals = Get-Values $s
    foreach ($p in $s.Params) {
        if ($p.Mandatory -and -not "$($vals[$p.Name])".Trim()) {
            [void][Windows.MessageBox]::Show($window, "Scegli prima un valore per ""$($p.Label)"".", $s.Title, 'OK', 'Information')
            return $false
        }
    }
    return $true
}

# Lo script di avvio: imposta la codifica, passa i parametri scelti e termina con l'exit code dello script.
# Interno: UTF-8 su input e output (le risposte e l'output con le accentate). Esterno: titolo della console e, alla
# fine, l'esito e l'attesa di Invio, così la console non si chiude prima che si legga.
function New-Wrapper($s, $arguments, [string]$command, [bool]$external) {
    $assign = foreach ($a in $arguments) {
        $literal = if ($a.Value -is [bool]) { '$true' }
        elseif ($a.Value -is [array]) { '@(' + (($a.Value | ForEach-Object { ConvertTo-Literal $_ }) -join ', ') + ')' }
        else { ConvertTo-Literal $a.Value }
        "`$params[$(ConvertTo-Literal $a.Name)] = $literal"
    }
    $lines = New-Object System.Collections.Generic.List[string]
    if ($external) {
        $lines.Add("`$Host.UI.RawUI.WindowTitle = $(ConvertTo-Literal "AnimeWatch - $($s.Title)")")
        $lines.Add("Write-Host $(ConvertTo-Literal "> $command") -ForegroundColor DarkGray")
    } else {
        $lines.Add('[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)')
        $lines.Add('[Console]::InputEncoding = [Text.UTF8Encoding]::new($false)')
        $lines.Add('$OutputEncoding = [Text.UTF8Encoding]::new($false)')
    }
    $lines.Add("`$ProgressPreference = 'SilentlyContinue'")
    $lines.Add("Set-Location -LiteralPath $(ConvertTo-Literal $root)")
    $lines.Add('$params = @{}')
    foreach ($l in $assign) { $lines.Add($l) }
    $lines.Add('$code = 1')
    $lines.Add('try {')
    $lines.Add('    $global:LASTEXITCODE = 0')
    $lines.Add("    & $(ConvertTo-Literal $s.Path) @params")
    $lines.Add('    $code = [int]$LASTEXITCODE')
    $lines.Add('} catch {')
    $lines.Add('    Write-Host ($_ | Out-String) -ForegroundColor Red')
    $lines.Add('}')
    if ($external) {
        $lines.Add("Write-Host ''")
        $lines.Add("if (`$code -eq 0) { Write-Host ' FATTO ' -ForegroundColor White -BackgroundColor DarkGreen }")
        $lines.Add("else { Write-Host (' ERRORE (codice {0}) ' -f `$code) -ForegroundColor White -BackgroundColor DarkRed }")
        $lines.Add("Write-Host 'Premi Invio per chiudere la finestra.' -ForegroundColor DarkGray")
        $lines.Add('[void](Read-Host)')
    }
    $lines.Add('exit $code')
    $null = New-Item -ItemType Directory -Force -Path $tempDir
    $path = Join-Path $tempDir ("run-{0}.ps1" -f [guid]::NewGuid().ToString('N'))
    # Con BOM: PowerShell 5.1 senza BOM legge male le accentate.
    [IO.File]::WriteAllText($path, ($lines -join "`r`n"), [Text.UTF8Encoding]::new($true))
    return $path
}

function Start-ScriptRun {
    $s = $script:current
    if (-not $s -or -not (Test-Mandatory $s)) { return }
    $arguments = @(Get-Arguments $s)
    $command = Format-Command $s $arguments
    $wrapper = New-Wrapper $s $arguments $command $false
    $runner = [AwRunner]::new('powershell.exe', "-NoProfile -ExecutionPolicy Bypass -File `"$wrapper`"", $root)
    $run = [pscustomobject]@{
        Title = $s.Title; Group = $s.Group; Command = $command; Runner = $runner; Wrapper = $wrapper
        Lines = New-Object 'System.Collections.ObjectModel.ObservableCollection[object]'
        Pending = ''; PendingShown = $false; LastOutput = [DateTime]::Now; ExitedAt = $null
        Status = 'running'; Done = $false; Stopped = $false; Chip = $null; Dot = $null; Started = (Get-Date -Format 'HH:mm:ss')
    }
    Add-Line $run "> $command" 'Faint'
    Add-RunChip $run
    $script:runs.Add($run)
    Select-Run $run
    [void]$inputBox.Focus()
}

function Start-ExternalRun {
    $s = $script:current
    if (-not $s -or -not (Test-Mandatory $s)) { return }
    $arguments = @(Get-Arguments $s)
    $wrapper = New-Wrapper $s $arguments (Format-Command $s $arguments) $true
    Start-Process powershell.exe -WorkingDirectory $root -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$wrapper`"")
}

# ---- Output ----------------------------------------------------------------------------------------------------------

function Get-LineColor([string]$t) {
    # Righe del logcat ("11-11 23:24:00.349  2815 13423 W Tag: ..."): il colore viene dal livello.
    if ($t -cmatch '^\d\d-\d\d \d\d:\d\d:\d\d\.\d+\s+\d+\s+\d+ ([VDIWEF]) ') {
        switch -CaseSensitive ($Matches[1]) {
            'E' { return 'Red' }
            'F' { return 'Red' }
            'W' { return 'Yellow' }
            'V' { return 'Muted' }
            'D' { return 'Muted' }
            default { return 'Text' }
        }
    }
    if ($t -match '^\s*(errore|interrott|motivo:|error\b|fatal\b|✖|×)|\bFAILED\b|What went wrong|\bexception\b') { return 'Red' }
    if ($t -match '^\s*(!!|warning\b|warn\b|attenzione|w:\s)') { return 'Yellow' }
    # "+" è la spunta della Firebase CLI quando non ha un terminale.
    if ($t -match '^\s*(fatto|ok\b|✔|✓|success|\+\s)|BUILD SUCCESSFUL|\binstallata\b|^\s*collegato') { return 'Green' }
    if ($t -match '^\s*(==>|═|─)') { return 'AccentText' }
    return 'Text'
}

# Un \r senza \n riporta all'inizio della riga (barre di avanzamento): conta l'ultimo pezzo non vuoto.
function Resolve-CarriageReturn([string]$t) {
    if ($t -notmatch "`r") { return $t }
    $pieces = @($t -split "`r" | Where-Object { $_ })
    if ($pieces.Count) { return $pieces[-1] }
    return ''
}

function Add-Line($run, [string]$text, [string]$color = '') {
    if (-not $color) { $color = Get-LineColor $text }
    $run.Lines.Add([pscustomobject]@{ Text = $text; Brush = $brush[$color] })
    if ($run.Lines.Count -gt $maxLines) {
        for ($i = 0; $i -lt 500; $i++) { $run.Lines.RemoveAt(0) }
    }
}

# L'ultima riga senza a capo (un prompt, per esempio) si mostra subito e si sostituisce quando arriva il resto.
function Add-Output($run, [string]$chunk) {
    $text = $run.Pending + ($ansi.Replace($chunk, '') -replace "`r`n", "`n")
    if ($run.PendingShown) {
        $run.Lines.RemoveAt($run.Lines.Count - 1)
        $run.PendingShown = $false
    }
    $parts = $text -split "`n"
    for ($i = 0; $i -lt $parts.Count - 1; $i++) { Add-Line $run (Resolve-CarriageReturn $parts[$i]) }
    $run.Pending = $parts[-1]
    $shown = Resolve-CarriageReturn $run.Pending
    if ($shown) {
        Add-Line $run $shown
        $run.PendingShown = $true
    }
}

function Test-AtBottom {
    if (-not $script:outputScroll) { return $true }
    return $script:outputScroll.VerticalOffset -ge $script:outputScroll.ScrollableHeight - 1
}

function Update-Runs {
    $now = [DateTime]::Now
    foreach ($run in $script:runs) {
        if ($run.Done) { continue }
        $follow = ($run -eq $script:selectedRun) -and (Test-AtBottom)
        $chunk = $run.Runner.Drain()
        if ($chunk) {
            Add-Output $run $chunk
            $run.LastOutput = $now
            if ($run.Status -eq 'waiting') { Set-RunStatus $run 'running' }
            if ($follow -and $run.Lines.Count) { $outputList.ScrollIntoView($run.Lines[$run.Lines.Count - 1]) }
        }
        if ($run.Runner.HasExited) {
            # Un giro di timer in più per raccogliere l'output rimasto nei buffer.
            if (-not $run.ExitedAt) { $run.ExitedAt = $now; continue }
            Complete-Run $run
            if ($follow -and $run.Lines.Count) { $outputList.ScrollIntoView($run.Lines[$run.Lines.Count - 1]) }
        } elseif ($run.Status -eq 'running' -and $run.Pending.Trim() -and $run.Pending -notmatch "`r" -and
            ($now - $run.LastOutput).TotalMilliseconds -gt 700) {
            # Una riga lasciata a metà che non si completa: lo script aspetta una risposta.
            Set-RunStatus $run 'waiting'
        }
    }
}

function Complete-Run($run) {
    if ($run.PendingShown) { $run.PendingShown = $false }
    elseif ($run.Pending.Trim()) { Add-Line $run (Resolve-CarriageReturn $run.Pending) }
    $run.Pending = ''
    $run.Done = $true
    $code = $run.Runner.ExitCode
    Add-Line $run '' 'Text'
    if ($run.Stopped) {
        Add-Line $run 'FERMATO' 'Muted'
        Set-RunStatus $run 'stopped'
    } elseif ($code -eq 0) {
        Add-Line $run 'FATTO' 'Green'
        Set-RunStatus $run 'ok'
    } else {
        Add-Line $run "ERRORE (codice $code)" 'Red'
        Set-RunStatus $run 'error'
    }
    Remove-Item -LiteralPath $run.Wrapper -Force -ErrorAction SilentlyContinue
}

function Get-StatusInfo($run) {
    switch ($run.Status) {
        'running' { return @{ Text = 'in corso'; Color = 'AccentText' } }
        'waiting' { return @{ Text = 'aspetta una risposta'; Color = 'Yellow' } }
        'ok' { return @{ Text = 'completato'; Color = 'Green' } }
        'stopped' { return @{ Text = 'fermato'; Color = 'Muted' } }
        default { return @{ Text = "errore (codice $($run.Runner.ExitCode))"; Color = 'Red' } }
    }
}

function Set-RunStatus($run, [string]$status) {
    $run.Status = $status
    $info = Get-StatusInfo $run
    $run.Dot.Foreground = $brush[$info.Color]
    $run.Chip.ToolTip = "$($run.Title): $($info.Text)"
    if ($run -eq $script:selectedRun) { Update-OutputHeader }
}

# Ferma lo script e i programmi che ha lanciato, tranne i server che servono anche dopo: il server adb (terrebbe
# giù il collegamento Wi-Fi del telefono) e il daemon di Gradle (si riusa per le build successive).
function Stop-ProcessTree([int]$rootId) {
    $children = @{}
    foreach ($p in (Get-CimInstance Win32_Process -Property ProcessId, ParentProcessId, CommandLine)) {
        $parent = [int]$p.ParentProcessId
        if (-not $children.ContainsKey($parent)) { $children[$parent] = New-Object System.Collections.Generic.List[object] }
        $children[$parent].Add($p)
    }
    $order = New-Object System.Collections.Generic.List[int]
    $seen = New-Object System.Collections.Generic.HashSet[int]
    $stack = New-Object System.Collections.Generic.Stack[int]
    $stack.Push($rootId)
    while ($stack.Count) {
        $id = $stack.Pop()
        if (-not $seen.Add($id)) { continue }
        $order.Add($id)
        if (-not $children.ContainsKey($id)) { continue }
        foreach ($c in $children[$id]) {
            if ("$($c.CommandLine)" -match 'fork-server|GradleDaemon') { continue }
            $stack.Push([int]$c.ProcessId)
        }
    }
    for ($i = $order.Count - 1; $i -ge 0; $i--) { Stop-Process -Id $order[$i] -Force -ErrorAction SilentlyContinue }
}

function Stop-Run($run) {
    if (-not $run -or $run.Done) { return }
    $run.Stopped = $true
    Stop-ProcessTree $run.Runner.Id
}

function Send-Input {
    $run = $script:selectedRun
    if (-not $run -or $run.Done) { return }
    $run.Runner.Send($inputBox.Text)
    $inputBox.Text = ''
    $run.LastOutput = [DateTime]::Now
    if ($run.Status -eq 'waiting') { Set-RunStatus $run 'running' }
}

function Add-RunChip($run) {
    $panel = New-Object Windows.Controls.StackPanel -Property @{ Orientation = 'Horizontal' }
    $dot = New-Text '●' 'AccentText' 11
    $dot.VerticalAlignment = 'Center'
    $title = New-Text $run.Title 'Text' 12 'SemiBold'
    $title.TextWrapping = 'NoWrap'
    $title.Margin = [Windows.Thickness]::new(7, 0, 0, 0)
    $time = New-Text $run.Started 'Faint' 11
    $time.Margin = [Windows.Thickness]::new(8, 1, 0, 0)
    foreach ($el in $dot, $title, $time) { [void]$panel.Children.Add($el) }
    # Ogni scheda ha il colore del gruppo dello script.
    $theme = Get-Theme $run.Group
    $chip = New-Object Windows.Controls.ListBoxItem -Property @{
        Content = $panel; Tag = $run; Background = $theme.Soft; BorderBrush = $theme.Solid
    }
    $run.Chip = $chip
    $run.Dot = $dot
    $runTabs.Items.Insert(0, $chip)
    $runTabs.Visibility = 'Visible'
}

function Select-Run($run) {
    $script:selectedRun = $run
    if ($run) {
        $outputList.ItemsSource = $run.Lines
        if ($runTabs.SelectedItem -ne $run.Chip) { $runTabs.SelectedItem = $run.Chip }
        if ($run.Lines.Count) { $outputList.ScrollIntoView($run.Lines[$run.Lines.Count - 1]) }
    } else {
        $outputList.ItemsSource = $null
    }
    Update-OutputHeader
}

function Update-OutputHeader {
    $run = $script:selectedRun
    $emptyOutput.Visibility = $(if ($run) { 'Collapsed' } else { 'Visible' })
    $outputTitle.Text = $(if ($run) { $run.Title } else { 'Output' })
    $outputTitle.Foreground = $(if ($run) { (Get-Theme $run.Group).Solid } else { $brush['Text'] })
    if ($run) {
        $info = Get-StatusInfo $run
        $outputStatus.Text = $info.Text
        $outputStatus.Foreground = $brush[$info.Color]
    } else {
        $outputStatus.Text = ''
    }
    $running = $run -and -not $run.Done
    $stopButton.Visibility = $(if ($running) { 'Visible' } else { 'Collapsed' })
    $copyOutputButton.IsEnabled = [bool]$run
    $clearButton.Visibility = $(if (@($script:runs | Where-Object { $_.Done }).Count) { 'Visible' } else { 'Collapsed' })
    $inputBox.IsEnabled = [bool]$running
    $sendButton.IsEnabled = [bool]$running
    $waiting = $running -and $run.Status -eq 'waiting'
    $inputHint.Text = $(if ($waiting) { 'Lo script aspetta una risposta: scrivila qui e premi Invio' }
        elseif ($running) { 'Risposta per lo script (Invio per inviare)' }
        else { 'Nessuno script in corso' })
    $inputBox.BorderBrush = $(if ($waiting) { $brush['Yellow'] } else { $brush['Border'] })
    $inputHint.Foreground = $(if ($waiting) { $brush['Yellow'] } else { $brush['Faint'] })
}

function Clear-Runs {
    foreach ($run in @($script:runs | Where-Object { $_.Done })) {
        $runTabs.Items.Remove($run.Chip)
        [void]$script:runs.Remove($run)
    }
    if (-not $script:runs.Contains($script:selectedRun)) {
        Select-Run $(if ($script:runs.Count) { $script:runs[$script:runs.Count - 1] } else { $null })
    }
    if (-not $script:runs.Count) { $runTabs.Visibility = 'Collapsed' }
    Update-OutputHeader
}

function Copy-Output([bool]$selectionOnly) {
    $run = $script:selectedRun
    if (-not $run) { return }
    $items = if ($selectionOnly -and $outputList.SelectedItems.Count) {
        @($outputList.SelectedItems | Sort-Object { $run.Lines.IndexOf($_) })
    } else { @($run.Lines) }
    $text = ($items | ForEach-Object { $_.Text }) -join "`r`n"
    if ($text) { [Windows.Clipboard]::SetText($text) }
}

# ---- Interfaccia -----------------------------------------------------------------------------------------------------

# Stile anime giapponese: cielo di notte, colori piatti come nella cel animation (arancione, blu, verde, giallo e un
# rosa sakura), contorni "a china" e ombre nette, niente bagliori. Ogni gruppo di script ha il suo colore, ripreso
# nell'elenco, nella scheda, nei passi, nel pulsante Esegui e nelle esecuzioni.
$palette = @{
    Background = '#141A33'; Panel = '#1A2142'; Raised = '#252D55'; Border = '#3A4475'
    Text = '#FFF6E5'; Muted = '#C2C6DD'; Faint = '#8A90B4'
    Accent = '#F2994A'; AccentSoft = '#3B2E3A'; AccentText = '#F7B977'
    Green = '#7BD88F'; Red = '#F28B82'; Yellow = '#F2C94C'
}
$brush = @{}
foreach ($k in $palette.Keys) { $brush[$k] = [Windows.Media.BrushConverter]::new().ConvertFromString($palette[$k]) }

function ConvertTo-Color([string]$hex) { [Windows.Media.Color][Windows.Media.ColorConverter]::ConvertFromString($hex) }

function New-Fade([string]$hex, [byte]$alpha) {
    $c = ConvertTo-Color $hex
    return [Windows.Media.Color]::FromArgb($alpha, $c.R, $c.G, $c.B)
}

# Colori dei gruppi, piatti: Color per pallini, icone, Esegui e numeri dei passi; Ink per il testo sopra quel colore.
# '_' per i gruppi nuovi.
$groupThemes = @{}
foreach ($t in @(
        @{ Name = 'Telefono'; Color = '#5B9BD5'; Ink = '#0A1A2E' }
        @{ Name = 'App'; Color = '#6CC070'; Ink = '#0E2410' }
        @{ Name = 'Firebase'; Color = '#F2C94C'; Ink = '#2B2000' }
        @{ Name = 'Proxy'; Color = '#F2994A'; Ink = '#2B1400' }
        @{ Name = 'Release'; Color = '#EE8FA6'; Ink = '#2B0E15' }
        @{ Name = '_'; Color = '#9AA5B1'; Ink = '#141A33' })) {
    $groupThemes[$t.Name] = @{
        Solid = [Windows.Media.SolidColorBrush]::new((ConvertTo-Color $t.Color))
        Soft = [Windows.Media.SolidColorBrush]::new((New-Fade $t.Color 0x2E))
        # Intestazione della scheda: una velatura del colore che svanisce verso destra.
        Header = [Windows.Media.LinearGradientBrush]::new((New-Fade $t.Color 0x38), (New-Fade $t.Color 0x00), 0)
        Ink = [Windows.Media.SolidColorBrush]::new((ConvertTo-Color $t.Ink))
    }
}

function Get-Theme([string]$group) {
    if ($groupThemes.ContainsKey($group)) { return $groupThemes[$group] }
    return $groupThemes['_']
}

function Invoke-Safe([scriptblock]$block) {
    try { & $block } catch {
        [void][Windows.MessageBox]::Show("$($_.Exception.Message)`n`n$($_.InvocationInfo.PositionMessage)", 'Script', 'OK', 'Error')
    }
}

# $color: una chiave della palette o direttamente un pennello (i colori dei gruppi).
function New-Text([string]$text, $color = 'Text', [double]$size = 13, [string]$weight = 'Normal') {
    $fg = if ($color -is [Windows.Media.Brush]) { $color } else { $brush[$color] }
    New-Object Windows.Controls.TextBlock -Property @{
        Text = $text; Foreground = $fg; FontSize = $size; FontWeight = [Windows.FontWeights]::$weight
        TextWrapping = 'Wrap'
    }
}

function New-Icon([char]$glyph, $color = 'Muted', [double]$size = 14) {
    $icon = New-Text ([string]$glyph) $color $size
    $icon.FontFamily = [Windows.Media.FontFamily]::new('Segoe MDL2 Assets')
    $icon.VerticalAlignment = 'Center'
    return $icon
}

function Get-GroupIcon([string]$group) {
    if ($groupIcons.ContainsKey($group)) { return $groupIcons[$group] }
    return $defaultIcon
}

function Update-List([string]$keepPath = $(if ($script:current) { $script:current.Path } else { '' })) {
    $filter = $filterBox.Text.Trim()
    $scriptList.Items.Clear()
    $group = $null
    $selected = $null
    foreach ($s in $script:scripts) {
        $haystack = "$($s.Title) $($s.Name) $($s.Synopsis) $($s.Group)"
        if ($filter -and $haystack.IndexOf($filter, [StringComparison]::OrdinalIgnoreCase) -lt 0) { continue }
        $theme = Get-Theme $s.Group
        if ($s.Group -ne $group) {
            $group = $s.Group
            # Etichetta del gruppo: una pillola nel suo colore.
            $pill = New-Object Windows.Controls.Border -Property @{
                Background = $theme.Soft; CornerRadius = [Windows.CornerRadius]::new(10)
                Padding = [Windows.Thickness]::new(9, 3, 11, 3); HorizontalAlignment = 'Left'
            }
            $row = New-Object Windows.Controls.StackPanel -Property @{ Orientation = 'Horizontal' }
            [void]$row.Children.Add((New-Icon (Get-GroupIcon $group) $theme.Solid 11))
            $label = New-Text $group.ToUpper() $theme.Solid 11 'Bold'
            $label.Margin = [Windows.Thickness]::new(7, 0, 0, 0)
            [void]$row.Children.Add($label)
            $pill.Child = $row
            [void]$scriptList.Items.Add((New-Object Windows.Controls.ListBoxItem -Property @{
                Content = $pill; Focusable = $false; IsHitTestVisible = $false; Padding = [Windows.Thickness]::new(4, 12, 4, 4)
            }))
        }
        # Solo il titolo, con un pallino sfumato nel colore del gruppo: la spiegazione sta nella scheda a destra.
        $row = New-Object Windows.Controls.StackPanel -Property @{ Orientation = 'Horizontal' }
        $dot = New-Object Windows.Shapes.Ellipse -Property @{
            Width = 9; Height = 9; Fill = $theme.Solid; VerticalAlignment = 'Center'
        }
        [void]$row.Children.Add($dot)
        $title = New-Text $s.Title 'Text' 14 'SemiBold'
        $title.Margin = [Windows.Thickness]::new(11, 0, 0, 0)
        [void]$row.Children.Add($title)
        $item = New-Object Windows.Controls.ListBoxItem -Property @{
            Content = $row; Tag = $s; Padding = [Windows.Thickness]::new(12, 9, 10, 9)
            Background = $theme.Soft; BorderBrush = $theme.Solid
        }
        [void]$scriptList.Items.Add($item)
        if ($s.Path -eq $keepPath) { $selected = $item }
    }
    $noMatch.Visibility = $(if (@($scriptList.Items).Count) { 'Collapsed' } else { 'Visible' })
    if (-not $selected) {
        $selected = @($scriptList.Items | Where-Object { $_.Tag }) | Select-Object -First 1
    }
    if ($selected) { $scriptList.SelectedItem = $selected } else { Show-Script $null }
}

# Il comando non si mostra: lo copia "Copia comando", che lo ha come suggerimento.
function Update-Preview {
    if (-not $script:current) { return }
    $script:commandText = Format-Command $script:current @(Get-Arguments $script:current)
    $copyButton.ToolTip = $script:commandText
}

function New-TextInput($p, [string]$value) {
    # Un predefinito calcolato (es. $env:...) non dice niente a chi usa la finestra: si mostra solo se è un testo.
    $hint = 'facoltativo'
    if ($p.Default -and $p.Default -notmatch '^\$') { $hint = "se vuoto: $($p.Default)" }
    elseif ($p.IsArray) { $hint = 'valori separati da virgole' }
    $grid = New-Object Windows.Controls.Grid -Property @{
        Margin = [Windows.Thickness]::new(0, 6, 0, 0)
    }
    $box = New-Object Windows.Controls.TextBox -Property @{ Text = $value; Tag = $p.Name }
    $placeholder = New-Text $hint 'Faint' 13
    $placeholder.Margin = [Windows.Thickness]::new(11, 7, 0, 0)
    $placeholder.IsHitTestVisible = $false
    $placeholder.Visibility = $(if ($value) { 'Collapsed' } else { 'Visible' })
    $box.Add_TextChanged({
        param($src, $e)
        Invoke-Safe {
            (Get-Values $script:current)[$src.Tag] = $src.Text
            $src.Parent.Children[1].Visibility = $(if ($src.Text) { 'Collapsed' } else { 'Visible' })
            Update-Preview
        }
    })
    $box.Add_KeyDown({
        param($src, $e)
        if ($e.Key -eq 'Return') { $e.Handled = $true; Invoke-Safe { Start-ScriptRun } }
    })
    [void]$grid.Children.Add($box)
    [void]$grid.Children.Add($placeholder)
    return $grid
}

function New-Choice($p, [string]$value) {
    $row = New-Object Windows.Controls.WrapPanel -Property @{ Margin = [Windows.Thickness]::new(0, 6, 0, 0) }
    foreach ($c in $p.Choices) {
        $button = New-Object Windows.Controls.Primitives.ToggleButton -Property @{
            Content = $c; Tag = @{ Param = $p.Name; Value = $c }; IsChecked = ($c -eq $value)
            Style = $window.FindResource('Segment')
        }
        # Un solo valore acceso; ritoccare quello acceso lo spegne (e il parametro non si passa).
        $button.Add_Click({
            param($src, $e)
            Invoke-Safe {
                $vals = Get-Values $script:current
                $vals[$src.Tag.Param] = $(if ($src.IsChecked) { $src.Tag.Value } else { '' })
                foreach ($b in $src.Parent.Children) { $b.IsChecked = ($b.Tag.Value -eq $vals[$src.Tag.Param]) }
                Update-Preview
            }
        })
        [void]$row.Children.Add($button)
    }
    return $row
}

# Nei commenti di aiuto le righe vanno a capo a ~120 colonne: qui si riuniscono. Restano i paragrafi (riga vuota)
# e le voci di elenco ("- ..." o "1. ..."), ognuna sulla sua riga.
function Join-Lines([string]$text, [string]$paragraphGap = "`n`n") {
    $paragraphs = foreach ($paragraph in ($text -split '\n\s*\n')) {
        $lines = New-Object System.Collections.Generic.List[string]
        foreach ($line in ($paragraph -split '\n')) {
            $t = $line.Trim()
            if (-not $t) { continue }
            if ($lines.Count -and $t -notmatch '^(-|\d+[.)])\s') { $lines[$lines.Count - 1] += " $t" }
            else { $lines.Add($t) }
        }
        $lines -join "`n"
    }
    return (@($paragraphs) -join $paragraphGap).Trim()
}

# "1. ..." su più righe -> un elenco di passi; le righe che non iniziano con un numero continuano il passo prima.
function Get-Steps([string]$text) {
    $steps = New-Object System.Collections.Generic.List[string]
    foreach ($line in ($text -split '\n')) {
        $t = $line.Trim()
        if (-not $t) { continue }
        if ($t -match '^\d+[.)]\s+(.*)$') { $steps.Add($Matches[1]) }
        elseif ($steps.Count) { $steps[$steps.Count - 1] += " $t" }
        else { $steps.Add($t) }
    }
    return $steps
}

function New-Step([int]$number, [string]$text, $theme) {
    $grid = New-Object Windows.Controls.Grid -Property @{ Margin = [Windows.Thickness]::new(0, 0, 0, 10) }
    $grid.ColumnDefinitions.Add((New-Object Windows.Controls.ColumnDefinition -Property @{ Width = [Windows.GridLength]::Auto }))
    $grid.ColumnDefinitions.Add((New-Object Windows.Controls.ColumnDefinition -Property @{ Width = [Windows.GridLength]::new(1, 'Star') }))
    $badge = New-Object Windows.Controls.Border -Property @{
        Width = 26; Height = 26; CornerRadius = [Windows.CornerRadius]::new(13); Background = $theme.Solid
        VerticalAlignment = 'Top'
    }
    $digit = New-Text "$number" $theme.Ink 12 'Bold'
    $digit.HorizontalAlignment = 'Center'
    $digit.VerticalAlignment = 'Center'
    $badge.Child = $digit
    $body = New-Text $text 'Text' 14
    $body.Margin = [Windows.Thickness]::new(12, 3, 0, 0)
    $body.LineHeight = 21
    [Windows.Controls.Grid]::SetColumn($body, 1)
    [void]$grid.Children.Add($badge)
    [void]$grid.Children.Add($body)
    return $grid
}

function Show-Script($s) {
    $script:current = $s
    $paramPanel.Children.Clear()
    $stepsPanel.Children.Clear()
    $detail.Visibility = $(if ($s) { 'Visible' } else { 'Collapsed' })
    $noScript.Visibility = $(if ($s) { 'Collapsed' } else { 'Visible' })
    if (-not $s) { return }

    $theme = Get-Theme $s.Group
    $detailHeader.Background = $theme.Header
    $iconBox.Background = $theme.Solid
    $groupIcon.Foreground = $theme.Ink
    $groupIcon.Text = [string](Get-GroupIcon $s.Group)
    $groupText.Text = $s.Group.ToUpper()
    $groupText.Foreground = $theme.Solid
    foreach ($caption in $descCaption, $stepsCaption, $optionsCaption) { $caption.Foreground = $theme.Solid }
    $runButton.Background = $theme.Solid
    $runButton.Foreground = $theme.Ink
    $titleText.Text = $s.Title
    $fileText.Text = "scripts\$($s.Name).ps1"
    $synopsisText.Text = Join-Lines $s.Synopsis

    $descriptionText.Text = Join-Lines $s.Description
    $descriptionSection.Visibility = $(if ($s.Description) { 'Visible' } else { 'Collapsed' })
    $n = 0
    foreach ($step in (Get-Steps $s.Steps)) { [void]$stepsPanel.Children.Add((New-Step (++$n) $step $theme)) }
    $stepsSection.Visibility = $(if ($n) { 'Visible' } else { 'Collapsed' })
    $optionsPane.Visibility = $(if ($s.Params.Count) { 'Visible' } else { 'Collapsed' })

    $vals = Get-Values $s
    foreach ($p in $s.Params) {
        $block = New-Object Windows.Controls.StackPanel -Property @{ Margin = [Windows.Thickness]::new(0, 0, 0, 16) }
        $label = $p.Label
        if ($p.Mandatory) { $label += '  ·  obbligatorio' }
        switch ($p.Kind) {
            'switch' {
                $check = New-Object Windows.Controls.CheckBox -Property @{
                    Content = $label; IsChecked = [bool]$vals[$p.Name]; Tag = $p.Name
                }
                $check.Add_Click({
                    param($src, $e)
                    Invoke-Safe { (Get-Values $script:current)[$src.Tag] = [bool]$src.IsChecked; Update-Preview }
                })
                [void]$block.Children.Add($check)
            }
            'choice' {
                [void]$block.Children.Add((New-Text $label 'Text' 13 'SemiBold'))
                [void]$block.Children.Add((New-Choice $p ([string]$vals[$p.Name])))
            }
            default {
                [void]$block.Children.Add((New-Text $label 'Text' 13 'SemiBold'))
                [void]$block.Children.Add((New-TextInput $p ([string]$vals[$p.Name])))
            }
        }
        if ($p.Help) {
            $help = New-Text (Join-Lines $p.Help "`n") 'Muted' 13
            $help.Margin = [Windows.Thickness]::new($(if ($p.Kind -eq 'switch') { 46 } else { 0 }), 4, 0, 0)
            [void]$block.Children.Add($help)
        }
        [void]$paramPanel.Children.Add($block)
    }
    $detailScroll.ScrollToTop()
    Update-Preview
}

function Open-Script {
    if (-not $script:current) { return }
    $quoted = '"{0}"' -f $script:current.Path
    $code = Get-Command code.cmd -ErrorAction SilentlyContinue
    if ($code) { Start-Process -FilePath $code.Source -ArgumentList $quoted -WindowStyle Hidden }
    else { Start-Process notepad.exe -ArgumentList $quoted }
}

# Dopo una ricarica la scheda si ridisegna anche se resta selezionato lo stesso script: può essere cambiato.
function Import-Scripts {
    $keep = ''
    if ($script:current) { $keep = $script:current.Path }
    $script:scripts = Get-Scripts
    $script:current = $null
    Update-List $keep
}

function Get-SavedFont {
    try {
        $name = (Get-Content -LiteralPath $settingsFile -Raw -Encoding UTF8 | ConvertFrom-Json).font
        if ($name -and $uiFonts.Contains($name)) { return $name }
    } catch { }
    return @($uiFonts.Keys)[0]
}

function Set-UiFont([string]$name, [bool]$save = $true) {
    $script:uiFont = $name
    $window.Resources['UiFont'] = $uiFonts[$name]
    $fontButton.ToolTip = "Font della finestra: $name"
    if (-not $save) { return }
    try {
        [void](New-Item -ItemType Directory -Force -Path (Split-Path -Parent $settingsFile))
        @{ font = $name } | ConvertTo-Json | Set-Content -LiteralPath $settingsFile -Encoding UTF8
    } catch { }   # senza un posto dove salvarla, la scelta vale fino alla chiusura
}

# Una riga per font, scritta nel font stesso: si vede com'è prima di sceglierlo.
function Show-FontPicker {
    $fontPanel.Children.Clear()
    foreach ($name in $uiFonts.Keys) {
        $item = New-Object Windows.Controls.Primitives.ToggleButton -Property @{
            Style = $window.FindResource('Segment'); Tag = $name; IsChecked = ($name -eq $script:uiFont)
            FontFamily = $uiFonts[$name]; FontSize = 14; HorizontalContentAlignment = 'Left'
            HorizontalAlignment = 'Stretch'; MinWidth = 230; Margin = [Windows.Thickness]::new(0, 0, 0, 6)
            Content = "$name · アニメ"
        }
        $item.Add_Click({
            param($src)
            Invoke-Safe { Set-UiFont $src.Tag; $fontPopup.IsOpen = $false }
        })
        [void]$fontPanel.Children.Add($item)
    }
    $fontPopup.IsOpen = $true
}

function Find-ScrollViewer($element) {
    if ($element -is [Windows.Controls.ScrollViewer]) { return $element }
    for ($i = 0; $i -lt [Windows.Media.VisualTreeHelper]::GetChildrenCount($element); $i++) {
        $found = Find-ScrollViewer ([Windows.Media.VisualTreeHelper]::GetChild($element, $i))
        if ($found) { return $found }
    }
    return $null
}

[xml]$xaml = @'
<Window xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
        xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"
        Title="AnimeWatch · script" Width="1180" Height="900" MinWidth="900" MinHeight="640"
        WindowStartupLocation="CenterScreen" Background="#141A33"
        FontFamily="{DynamicResource UiFont}" FontSize="13" Foreground="#FFF6E5" UseLayoutRounding="True"
        TextOptions.TextFormattingMode="Display">
  <Window.Resources>
    <!-- Stile anime: colori piatti (cel), contorni "a china" e ombre nette spostate, niente bagliori -->
    <SolidColorBrush x:Key="Ink" Color="#0A0D1E"/>
    <SolidColorBrush x:Key="Panel" Color="#1A2142"/>
    <SolidColorBrush x:Key="Raised" Color="#252D55"/>
    <SolidColorBrush x:Key="Line" Color="#3A4475"/>
    <SolidColorBrush x:Key="Text" Color="#FFF6E5"/>
    <SolidColorBrush x:Key="Muted" Color="#C2C6DD"/>
    <SolidColorBrush x:Key="Faint" Color="#8A90B4"/>
    <SolidColorBrush x:Key="Accent" Color="#F2994A"/>
    <SolidColorBrush x:Key="AccentSoft" Color="#3B2E3A"/>
    <FontFamily x:Key="Icons">Segoe MDL2 Assets</FontFamily>
    <!-- Sostituito all'avvio con il font scelto ($uiFonts) -->
    <FontFamily x:Key="UiFont">Yu Gothic UI, Segoe UI</FontFamily>
    <Geometry x:Key="Cloud">M 0,40 C 0,28 12,22 22,26 C 26,10 50,6 60,20 C 68,8 92,10 94,28 C 106,26 116,34 114,44 L 0,44 Z</Geometry>

    <Style TargetType="ToolTip">
      <!-- I tooltip sono finestre a parte: il font della finestra non arriva da solo -->
      <Setter Property="FontFamily" Value="{DynamicResource UiFont}"/>
      <Setter Property="Background" Value="{StaticResource Raised}"/>
      <Setter Property="Foreground" Value="{StaticResource Text}"/>
      <Setter Property="BorderBrush" Value="{StaticResource Line}"/>
      <Setter Property="Padding" Value="8,4"/>
    </Style>

    <Style TargetType="Button">
      <Setter Property="Padding" Value="14,7"/>
      <Setter Property="Margin" Value="0,0,10,0"/>
      <Setter Property="Background" Value="{StaticResource Raised}"/>
      <Setter Property="BorderBrush" Value="{StaticResource Line}"/>
      <Setter Property="Foreground" Value="{StaticResource Text}"/>
      <Setter Property="Cursor" Value="Hand"/>
      <Setter Property="FocusVisualStyle" Value="{x:Null}"/>
      <Setter Property="Template">
        <Setter.Value>
          <ControlTemplate TargetType="Button">
            <Border x:Name="Bd" Background="{TemplateBinding Background}" BorderBrush="{TemplateBinding BorderBrush}"
                    BorderThickness="1.5" CornerRadius="10" Padding="{TemplateBinding Padding}">
              <ContentPresenter HorizontalAlignment="Center" VerticalAlignment="Center"/>
            </Border>
            <ControlTemplate.Triggers>
              <Trigger Property="IsMouseOver" Value="True"><Setter TargetName="Bd" Property="BorderBrush" Value="#F2994A"/></Trigger>
              <Trigger Property="IsKeyboardFocused" Value="True"><Setter TargetName="Bd" Property="BorderBrush" Value="#F2C94C"/></Trigger>
              <Trigger Property="IsPressed" Value="True"><Setter TargetName="Bd" Property="Opacity" Value="0.75"/></Trigger>
              <Trigger Property="IsEnabled" Value="False"><Setter TargetName="Bd" Property="Opacity" Value="0.4"/></Trigger>
            </ControlTemplate.Triggers>
          </ControlTemplate>
        </Setter.Value>
      </Setter>
    </Style>
    <!-- Esegui: colore piatto del gruppo, contorno a china e ombra netta; premuto si abbassa sull'ombra -->
    <Style x:Key="Primary" TargetType="Button" BasedOn="{StaticResource {x:Type Button}}">
      <Setter Property="FontWeight" Value="Bold"/>
      <Setter Property="Padding" Value="24,9"/>
      <Setter Property="FontSize" Value="14"/>
      <Setter Property="Margin" Value="0,0,14,3"/>
      <Setter Property="Template">
        <Setter.Value>
          <ControlTemplate TargetType="Button">
            <Grid>
              <Border Background="{StaticResource Ink}" CornerRadius="12" Margin="3,3,-3,-3"/>
              <Border x:Name="Bd" Background="{TemplateBinding Background}" BorderBrush="{StaticResource Ink}"
                      BorderThickness="2" CornerRadius="12" Padding="{TemplateBinding Padding}">
                <ContentPresenter HorizontalAlignment="Center" VerticalAlignment="Center"/>
              </Border>
            </Grid>
            <ControlTemplate.Triggers>
              <Trigger Property="IsMouseOver" Value="True"><Setter TargetName="Bd" Property="Opacity" Value="0.92"/></Trigger>
              <Trigger Property="IsPressed" Value="True">
                <Setter TargetName="Bd" Property="RenderTransform"><Setter.Value><TranslateTransform X="3" Y="3"/></Setter.Value></Setter>
              </Trigger>
            </ControlTemplate.Triggers>
          </ControlTemplate>
        </Setter.Value>
      </Setter>
    </Style>
    <Style x:Key="Danger" TargetType="Button" BasedOn="{StaticResource {x:Type Button}}">
      <Setter Property="Background" Value="#4A2230"/>
      <Setter Property="BorderBrush" Value="#F28B82"/>
      <Setter Property="Foreground" Value="#FFD6D2"/>
      <Setter Property="Padding" Value="12,4"/>
    </Style>
    <Style x:Key="Small" TargetType="Button" BasedOn="{StaticResource {x:Type Button}}">
      <Setter Property="Padding" Value="12,4"/>
    </Style>

    <Style x:Key="Segment" TargetType="ToggleButton">
      <Setter Property="Foreground" Value="{StaticResource Text}"/>
      <Setter Property="Background" Value="{StaticResource Raised}"/>
      <Setter Property="BorderBrush" Value="{StaticResource Line}"/>
      <Setter Property="Padding" Value="16,6"/>
      <Setter Property="Margin" Value="0,0,8,6"/>
      <Setter Property="MinWidth" Value="76"/>
      <Setter Property="Cursor" Value="Hand"/>
      <Setter Property="FocusVisualStyle" Value="{x:Null}"/>
      <Setter Property="Template">
        <Setter.Value>
          <ControlTemplate TargetType="ToggleButton">
            <Border x:Name="Bd" Background="{TemplateBinding Background}" BorderBrush="{TemplateBinding BorderBrush}"
                    BorderThickness="1.5" CornerRadius="16" Padding="{TemplateBinding Padding}">
              <ContentPresenter HorizontalAlignment="Center" VerticalAlignment="Center"/>
            </Border>
            <ControlTemplate.Triggers>
              <Trigger Property="IsMouseOver" Value="True"><Setter TargetName="Bd" Property="BorderBrush" Value="#F2994A"/></Trigger>
              <Trigger Property="IsChecked" Value="True">
                <Setter TargetName="Bd" Property="Background" Value="#F2994A"/>
                <Setter TargetName="Bd" Property="BorderBrush" Value="{StaticResource Ink}"/>
                <Setter Property="Foreground" Value="#2B1400"/>
                <Setter Property="FontWeight" Value="Bold"/>
              </Trigger>
            </ControlTemplate.Triggers>
          </ControlTemplate>
        </Setter.Value>
      </Setter>
    </Style>

    <!-- Interruttore al posto della casella -->
    <Style TargetType="CheckBox">
      <Setter Property="Foreground" Value="{StaticResource Text}"/>
      <Setter Property="FontWeight" Value="SemiBold"/>
      <Setter Property="Cursor" Value="Hand"/>
      <Setter Property="FocusVisualStyle" Value="{x:Null}"/>
      <Setter Property="Template">
        <Setter.Value>
          <ControlTemplate TargetType="CheckBox">
            <StackPanel Orientation="Horizontal" Background="Transparent">
              <Grid Width="38" Height="22" VerticalAlignment="Center">
                <Border x:Name="Track" CornerRadius="11" Background="#252D55" BorderBrush="#3A4475" BorderThickness="1.5"/>
                <Ellipse x:Name="Knob" Width="14" Height="14" Fill="#C2C6DD" HorizontalAlignment="Left" Margin="4,0,0,0"/>
              </Grid>
              <ContentPresenter Margin="10,0,0,0" VerticalAlignment="Center"/>
            </StackPanel>
            <ControlTemplate.Triggers>
              <Trigger Property="IsMouseOver" Value="True"><Setter TargetName="Track" Property="BorderBrush" Value="#F2994A"/></Trigger>
              <Trigger Property="IsChecked" Value="True">
                <Setter TargetName="Track" Property="Background" Value="#F2994A"/>
                <Setter TargetName="Track" Property="BorderBrush" Value="#0A0D1E"/>
                <Setter TargetName="Knob" Property="Fill" Value="#FFF6E5"/>
                <Setter TargetName="Knob" Property="HorizontalAlignment" Value="Right"/>
                <Setter TargetName="Knob" Property="Margin" Value="0,0,4,0"/>
              </Trigger>
            </ControlTemplate.Triggers>
          </ControlTemplate>
        </Setter.Value>
      </Setter>
    </Style>

    <Style TargetType="TextBox">
      <Setter Property="Background" Value="#11172F"/>
      <Setter Property="Foreground" Value="{StaticResource Text}"/>
      <Setter Property="BorderBrush" Value="{StaticResource Line}"/>
      <Setter Property="CaretBrush" Value="#F2C94C"/>
      <Setter Property="SelectionBrush" Value="{StaticResource Accent}"/>
      <Setter Property="Padding" Value="8,6"/>
      <Setter Property="Template">
        <Setter.Value>
          <ControlTemplate TargetType="TextBox">
            <Border x:Name="Bd" Background="{TemplateBinding Background}" BorderBrush="{TemplateBinding BorderBrush}"
                    BorderThickness="1.5" CornerRadius="10">
              <!-- Il Padding lo applica già il TextBox al testo: qui niente margine, o conterebbe due volte -->
              <ScrollViewer x:Name="PART_ContentHost" VerticalAlignment="Center"/>
            </Border>
            <ControlTemplate.Triggers>
              <Trigger Property="IsMouseOver" Value="True"><Setter TargetName="Bd" Property="BorderBrush" Value="#8A90B4"/></Trigger>
              <Trigger Property="IsKeyboardFocused" Value="True"><Setter TargetName="Bd" Property="BorderBrush" Value="#F2994A"/></Trigger>
              <Trigger Property="IsEnabled" Value="False"><Setter TargetName="Bd" Property="Opacity" Value="0.5"/></Trigger>
            </ControlTemplate.Triggers>
          </ControlTemplate>
        </Setter.Value>
      </Setter>
    </Style>

    <Style TargetType="ListBox">
      <Setter Property="Background" Value="Transparent"/>
      <Setter Property="BorderThickness" Value="0"/>
      <Setter Property="ScrollViewer.HorizontalScrollBarVisibility" Value="Disabled"/>
      <Setter Property="FocusVisualStyle" Value="{x:Null}"/>
    </Style>
    <!-- Voci dell'elenco: da selezionate prendono Background e BorderBrush della voce, cioè il colore del gruppo -->
    <Style TargetType="ListBoxItem">
      <Setter Property="Foreground" Value="{StaticResource Text}"/>
      <Setter Property="Background" Value="{StaticResource AccentSoft}"/>
      <Setter Property="BorderBrush" Value="{StaticResource Accent}"/>
      <Setter Property="FocusVisualStyle" Value="{x:Null}"/>
      <Setter Property="Cursor" Value="Hand"/>
      <Setter Property="Template">
        <Setter.Value>
          <ControlTemplate TargetType="ListBoxItem">
            <Border x:Name="Bd" Background="Transparent" BorderBrush="Transparent" BorderThickness="3,0,0,0"
                    CornerRadius="10" Margin="0,1" Padding="{TemplateBinding Padding}">
              <ContentPresenter/>
            </Border>
            <ControlTemplate.Triggers>
              <Trigger Property="IsMouseOver" Value="True"><Setter TargetName="Bd" Property="Background" Value="#12FFF6E5"/></Trigger>
              <Trigger Property="IsSelected" Value="True">
                <Setter TargetName="Bd" Property="Background" Value="{Binding Background, RelativeSource={RelativeSource TemplatedParent}}"/>
                <Setter TargetName="Bd" Property="BorderBrush" Value="{Binding BorderBrush, RelativeSource={RelativeSource TemplatedParent}}"/>
              </Trigger>
            </ControlTemplate.Triggers>
          </ControlTemplate>
        </Setter.Value>
      </Setter>
    </Style>
    <!-- Esecuzioni come schede tonde in fila, nel colore del gruppo dello script -->
    <Style x:Key="Chip" TargetType="ListBoxItem">
      <Setter Property="Cursor" Value="Hand"/>
      <Setter Property="FocusVisualStyle" Value="{x:Null}"/>
      <Setter Property="Foreground" Value="{StaticResource Text}"/>
      <Setter Property="Template">
        <Setter.Value>
          <ControlTemplate TargetType="ListBoxItem">
            <Border x:Name="Bd" Background="{TemplateBinding Background}" BorderBrush="{TemplateBinding BorderBrush}"
                    BorderThickness="1.5" CornerRadius="14" Padding="11,4" Margin="0,0,6,0" Opacity="0.6">
              <ContentPresenter/>
            </Border>
            <ControlTemplate.Triggers>
              <Trigger Property="IsMouseOver" Value="True"><Setter TargetName="Bd" Property="Opacity" Value="0.85"/></Trigger>
              <Trigger Property="IsSelected" Value="True">
                <Setter TargetName="Bd" Property="Opacity" Value="1"/>
                <Setter TargetName="Bd" Property="BorderThickness" Value="2"/>
              </Trigger>
            </ControlTemplate.Triggers>
          </ControlTemplate>
        </Setter.Value>
      </Setter>
    </Style>
    <!-- Righe dell'output: selezionabili (Ctrl+C), senza la barra colorata delle liste -->
    <Style x:Key="OutputLine" TargetType="ListBoxItem">
      <Setter Property="FocusVisualStyle" Value="{x:Null}"/>
      <Setter Property="Template">
        <Setter.Value>
          <ControlTemplate TargetType="ListBoxItem">
            <Border x:Name="Bd" Background="Transparent" Padding="12,0">
              <ContentPresenter/>
            </Border>
            <ControlTemplate.Triggers>
              <Trigger Property="IsSelected" Value="True"><Setter TargetName="Bd" Property="Background" Value="#2C3460"/></Trigger>
            </ControlTemplate.Triggers>
          </ControlTemplate>
        </Setter.Value>
      </Setter>
    </Style>

    <!-- Barra di scorrimento sottile -->
    <ControlTemplate x:Key="ThumbTemplate" TargetType="Thumb">
      <Border x:Name="T" Background="#3A4475" CornerRadius="4"/>
      <ControlTemplate.Triggers>
        <Trigger Property="IsMouseOver" Value="True"><Setter TargetName="T" Property="Background" Value="#F2994A"/></Trigger>
      </ControlTemplate.Triggers>
    </ControlTemplate>
    <Style TargetType="ScrollBar">
      <Setter Property="Width" Value="8"/>
      <Setter Property="MinWidth" Value="8"/>
      <Setter Property="Margin" Value="2"/>
      <Setter Property="Template">
        <Setter.Value>
          <ControlTemplate TargetType="ScrollBar">
            <Track x:Name="PART_Track" IsDirectionReversed="True">
              <Track.Thumb><Thumb Template="{StaticResource ThumbTemplate}"/></Track.Thumb>
            </Track>
          </ControlTemplate>
        </Setter.Value>
      </Setter>
      <Style.Triggers>
        <Trigger Property="Orientation" Value="Horizontal">
          <Setter Property="Width" Value="Auto"/>
          <Setter Property="MinWidth" Value="0"/>
          <Setter Property="Height" Value="8"/>
          <Setter Property="Template">
            <Setter.Value>
              <ControlTemplate TargetType="ScrollBar">
                <Track x:Name="PART_Track">
                  <Track.Thumb><Thumb Template="{StaticResource ThumbTemplate}"/></Track.Thumb>
                </Track>
              </ControlTemplate>
            </Setter.Value>
          </Setter>
        </Trigger>
      </Style.Triggers>
    </Style>

    <!-- Schede: contorno a china; l'ombra netta è un secondo bordo spostato dietro (vedi CardShadow) -->
    <Style x:Key="Card" TargetType="Border">
      <Setter Property="Background" Value="{StaticResource Panel}"/>
      <Setter Property="BorderBrush" Value="{StaticResource Ink}"/>
      <Setter Property="BorderThickness" Value="2"/>
      <Setter Property="CornerRadius" Value="16"/>
    </Style>
    <Style x:Key="CardShadow" TargetType="Border">
      <Setter Property="Background" Value="{StaticResource Ink}"/>
      <Setter Property="CornerRadius" Value="16"/>
      <Setter Property="Margin" Value="5,5,-5,-5"/>
      <Setter Property="Opacity" Value="0.85"/>
    </Style>
    <Style x:Key="Caption" TargetType="TextBlock">
      <Setter Property="FontSize" Value="11"/>
      <Setter Property="FontWeight" Value="Bold"/>
      <Setter Property="Foreground" Value="{StaticResource Faint}"/>
    </Style>
  </Window.Resources>

  <Grid>
    <!-- Cielo di notte: indaco in alto, orizzonte caldo in basso -->
    <Rectangle IsHitTestVisible="False">
      <Rectangle.Fill>
        <LinearGradientBrush StartPoint="0,0" EndPoint="0,1">
          <GradientStop Color="#121833" Offset="0"/>
          <GradientStop Color="#1C2347" Offset="0.55"/>
          <GradientStop Color="#3A2E52" Offset="0.85"/>
          <GradientStop Color="#6B4A4A" Offset="1"/>
        </LinearGradientBrush>
      </Rectangle.Fill>
    </Rectangle>
    <!-- Luna, stelle e nuvole: si vedono attorno alle schede e dietro l'intestazione -->
    <Canvas IsHitTestVisible="False">
      <Grid Canvas.Left="236" Canvas.Top="14" Width="42" Height="42">
        <Ellipse Fill="#FFF1C9"/>
        <Ellipse Fill="#121833" Margin="11,-5,-9,7"/>
      </Grid>
      <TextBlock Text="✦" Foreground="#FFF1C9" FontSize="12" Opacity="0.9" Canvas.Left="208" Canvas.Top="20"/>
      <TextBlock Text="✦" Foreground="#FFF1C9" FontSize="8" Opacity="0.7" Canvas.Left="286" Canvas.Top="64"/>
      <TextBlock Text="✦" Foreground="#F2C94C" FontSize="9" Opacity="0.8" Canvas.Left="182" Canvas.Top="48"/>
      <Ellipse Fill="#FFF1C9" Width="3" Height="3" Opacity="0.7" Canvas.Left="262" Canvas.Top="70"/>
      <Ellipse Fill="#FFF1C9" Width="2" Height="2" Opacity="0.6" Canvas.Left="300" Canvas.Top="12"/>
      <Ellipse Fill="#FFF1C9" Width="2" Height="2" Opacity="0.5" Canvas.Left="160" Canvas.Top="10"/>
    </Canvas>
    <Path Data="{StaticResource Cloud}" Fill="#2A2547" Opacity="0.9" Stretch="Fill" Width="260" Height="70"
          HorizontalAlignment="Left" VerticalAlignment="Bottom" Margin="-30,0,0,-8" IsHitTestVisible="False"/>
    <Path Data="{StaticResource Cloud}" Fill="#352C50" Opacity="0.9" Stretch="Fill" Width="200" Height="52"
          HorizontalAlignment="Left" VerticalAlignment="Bottom" Margin="150,0,0,-6" IsHitTestVisible="False"/>
    <Path Data="{StaticResource Cloud}" Fill="#2A2547" Opacity="0.9" Stretch="Fill" Width="300" Height="74"
          HorizontalAlignment="Right" VerticalAlignment="Bottom" Margin="0,0,-40,-10" IsHitTestVisible="False"/>

    <Grid Margin="18,18,24,24">
      <Grid.ColumnDefinitions>
        <ColumnDefinition Width="300"/>
        <ColumnDefinition Width="22"/>
        <ColumnDefinition Width="*"/>
      </Grid.ColumnDefinitions>

      <!-- Elenco degli script -->
      <DockPanel Grid.Column="0">
        <StackPanel DockPanel.Dock="Top" Margin="4,0,0,16">
          <TextBlock Text="AnimeWatch" FontSize="30" FontWeight="Bold" Foreground="#FFF6E5"/>
          <TextBlock Text="アニメウォッチ ・ スクリプト" FontSize="12" Foreground="#F2994A" Margin="2,0,0,2"/>
          <TextBlock Text="Scegli un'azione, regola le opzioni e premi Esegui." Foreground="{StaticResource Muted}"
                     Margin="2,4,0,0" TextWrapping="Wrap"/>
        </StackPanel>
        <Grid DockPanel.Dock="Top" Margin="0,0,0,12">
          <TextBox x:Name="FilterBox" Padding="30,7,8,7" ToolTip="Ctrl+F"/>
          <TextBlock Text="&#xE721;" FontFamily="{StaticResource Icons}" Foreground="#F2C94C"
                     Margin="11,0,0,0" VerticalAlignment="Center" IsHitTestVisible="False"/>
          <TextBlock x:Name="FilterHint" Text="Cerca uno script…" Foreground="{StaticResource Faint}" Margin="33,0,0,0"
                     VerticalAlignment="Center" IsHitTestVisible="False"/>
        </Grid>
        <DockPanel DockPanel.Dock="Bottom" Margin="0,14,0,0" LastChildFill="False">
          <Button x:Name="FontButton" DockPanel.Dock="Right" Margin="0" Padding="11,7" ToolTip="Font della finestra">
            <TextBlock Text="&#xE8D2;" FontFamily="{StaticResource Icons}" Foreground="#EE8FA6" FontSize="14"/>
          </Button>
          <Popup x:Name="FontPopup" PlacementTarget="{Binding ElementName=FontButton}" Placement="Top"
                 VerticalOffset="-8" StaysOpen="False" AllowsTransparency="True" PopupAnimation="Fade">
            <Grid Margin="0,0,6,6">
              <Border Style="{StaticResource CardShadow}"/>
              <Border Style="{StaticResource Card}" Padding="14,12,14,8" Background="{StaticResource Raised}">
                <StackPanel>
                  <TextBlock Text="フォント  FONT" Style="{StaticResource Caption}" Margin="2,0,0,10"/>
                  <StackPanel x:Name="FontPanel"/>
                </StackPanel>
              </Border>
            </Grid>
          </Popup>
          <Button x:Name="FolderButton" ToolTip="Apre la cartella scripts in Esplora file">
            <StackPanel Orientation="Horizontal">
              <TextBlock Text="&#xE838;" FontFamily="{StaticResource Icons}" VerticalAlignment="Center" Margin="0,0,8,0" Foreground="#F2C94C"/>
              <TextBlock Text="Cartella"/>
            </StackPanel>
          </Button>
          <Button x:Name="ReloadButton" ToolTip="Rilegge gli script (F5)">
            <StackPanel Orientation="Horizontal">
              <TextBlock Text="&#xE72C;" FontFamily="{StaticResource Icons}" VerticalAlignment="Center" Margin="0,0,8,0" Foreground="#5B9BD5"/>
              <TextBlock Text="Ricarica"/>
            </StackPanel>
          </Button>
        </DockPanel>
        <Grid>
          <Border Style="{StaticResource CardShadow}"/>
          <Border Style="{StaticResource Card}" Padding="8">
            <Grid>
              <ListBox x:Name="ScriptList"/>
              <TextBlock x:Name="NoMatch" Text="Nessuno script trovato." Foreground="{StaticResource Muted}"
                         HorizontalAlignment="Center" VerticalAlignment="Center" Visibility="Collapsed"/>
            </Grid>
          </Border>
        </Grid>
      </DockPanel>

      <Grid Grid.Column="2">
        <Grid.RowDefinitions>
          <RowDefinition Height="11*" MinHeight="300"/>
          <RowDefinition Height="18"/>
          <RowDefinition Height="9*" MinHeight="200"/>
        </Grid.RowDefinitions>

        <!-- Scheda dello script scelto -->
        <Border Style="{StaticResource CardShadow}"/>
        <Border Style="{StaticResource Card}">
          <Grid>
            <TextBlock x:Name="NoScript" Text="Nessuno script in scripts\." Foreground="{StaticResource Muted}"
                       HorizontalAlignment="Center" VerticalAlignment="Center" Visibility="Collapsed"/>
            <DockPanel x:Name="Detail">
              <!-- Intestazione: una velatura nel colore del gruppo -->
              <Border x:Name="DetailHeader" DockPanel.Dock="Top" CornerRadius="14,14,0,0" Padding="24,20,24,14">
                <Grid>
                  <Grid.ColumnDefinitions>
                    <ColumnDefinition Width="Auto"/>
                    <ColumnDefinition Width="*"/>
                  </Grid.ColumnDefinitions>
                  <Border x:Name="IconBox" Width="50" Height="50" CornerRadius="14" VerticalAlignment="Top"
                          BorderBrush="{StaticResource Ink}" BorderThickness="2">
                    <TextBlock x:Name="GroupIcon" FontFamily="{StaticResource Icons}" FontSize="22"
                               HorizontalAlignment="Center" VerticalAlignment="Center"/>
                  </Border>
                  <!-- Gruppo e file in piccolo, poi il titolo e sotto, come sottotitolo, il riassunto (.SYNOPSIS) -->
                  <StackPanel Grid.Column="1" Margin="16,0,0,0">
                    <StackPanel Orientation="Horizontal">
                      <TextBlock x:Name="GroupText" FontSize="11" FontWeight="Bold" VerticalAlignment="Center"/>
                      <TextBlock Text="·" FontSize="11" Foreground="{StaticResource Faint}" Margin="6,0" VerticalAlignment="Center"/>
                      <TextBlock x:Name="FileText" FontFamily="Consolas" FontSize="11" Foreground="{StaticResource Faint}" VerticalAlignment="Center"/>
                    </StackPanel>
                    <TextBlock x:Name="TitleText" FontSize="24" FontWeight="Bold" TextWrapping="Wrap" Margin="0,1,0,0"/>
                    <TextBlock x:Name="SynopsisText" TextWrapping="Wrap" Foreground="{StaticResource Muted}" FontSize="13.5" Margin="0,3,0,0"/>
                  </StackPanel>
                </Grid>
              </Border>

              <Border DockPanel.Dock="Bottom" BorderBrush="{StaticResource Line}" BorderThickness="0,1.5,0,0" Padding="24,14,24,14">
                <StackPanel Orientation="Horizontal">
                  <Button x:Name="RunButton" Style="{StaticResource Primary}" ToolTip="Esegue lo script: l'output compare nel pannello in basso (Ctrl+Invio)">
                    <StackPanel Orientation="Horizontal">
                      <TextBlock Text="&#xE768;" FontFamily="{StaticResource Icons}" VerticalAlignment="Center" Margin="0,0,10,0"/>
                      <TextBlock Text="Esegui"/>
                    </StackPanel>
                  </Button>
                  <Button x:Name="ExternalButton" ToolTip="Esegue lo script in una finestra PowerShell separata">
                    <StackPanel Orientation="Horizontal">
                      <TextBlock Text="&#xE756;" FontFamily="{StaticResource Icons}" VerticalAlignment="Center" Margin="0,0,8,0" Foreground="#6CC070"/>
                      <TextBlock Text="In una console"/>
                    </StackPanel>
                  </Button>
                  <Button x:Name="CopyButton">
                    <StackPanel Orientation="Horizontal">
                      <TextBlock Text="&#xE8C8;" FontFamily="{StaticResource Icons}" VerticalAlignment="Center" Margin="0,0,8,0" Foreground="#5B9BD5"/>
                      <TextBlock x:Name="CopyText" Text="Copia comando"/>
                    </StackPanel>
                  </Button>
                  <Button x:Name="EditButton" ToolTip="Apre lo script nell'editor">
                    <StackPanel Orientation="Horizontal">
                      <TextBlock Text="&#xE70F;" FontFamily="{StaticResource Icons}" VerticalAlignment="Center" Margin="0,0,8,0" Foreground="#F2C94C"/>
                      <TextBlock Text="Apri script"/>
                    </StackPanel>
                  </Button>
                </StackPanel>
              </Border>

              <!-- Spiegazione a sinistra, opzioni in una colonna a destra: le opzioni non tolgono altezza alla spiegazione -->
              <Grid Margin="0,14,0,0">
                <Grid.ColumnDefinitions>
                  <ColumnDefinition Width="*"/>
                  <ColumnDefinition Width="Auto"/>
                </Grid.ColumnDefinitions>
                <ScrollViewer x:Name="DetailScroll" VerticalScrollBarVisibility="Auto" Margin="0,0,4,8">
                  <StackPanel Margin="24,0,20,0">
                    <StackPanel x:Name="DescriptionSection" Margin="0,0,0,22">
                      <TextBlock x:Name="DescCaption" Text="説明  COSA FA" Style="{StaticResource Caption}" Margin="0,0,0,8"/>
                      <TextBlock x:Name="DescriptionText" TextWrapping="Wrap" Foreground="#E4E6F2" FontSize="14" LineHeight="22"/>
                    </StackPanel>
                    <StackPanel x:Name="StepsSection" Margin="0,0,0,8">
                      <TextBlock x:Name="StepsCaption" Text="使い方  COME SI USA" Style="{StaticResource Caption}" Margin="0,0,0,10"/>
                      <StackPanel x:Name="StepsPanel"/>
                    </StackPanel>
                  </StackPanel>
                </ScrollViewer>
                <Border x:Name="OptionsPane" Grid.Column="1" Width="330" BorderBrush="{StaticResource Line}"
                        BorderThickness="1.5,0,0,0" Margin="0,0,0,12">
                  <ScrollViewer VerticalScrollBarVisibility="Auto">
                    <StackPanel Margin="20,0,16,0">
                      <TextBlock x:Name="OptionsCaption" Text="設定  OPZIONI" Style="{StaticResource Caption}" Margin="0,0,0,10"/>
                      <StackPanel x:Name="ParamPanel"/>
                    </StackPanel>
                  </ScrollViewer>
                </Border>
              </Grid>
            </DockPanel>
          </Grid>
        </Border>

        <GridSplitter Grid.Row="1" Height="18" HorizontalAlignment="Stretch" Background="Transparent"
                      ResizeDirection="Rows" ToolTip="Trascina per ridimensionare"/>

        <!-- Output degli script -->
        <Border Grid.Row="2" Style="{StaticResource CardShadow}"/>
        <Border Grid.Row="2" Style="{StaticResource Card}">
          <DockPanel>
            <Grid DockPanel.Dock="Top" Margin="18,12,12,8">
              <Grid.ColumnDefinitions>
                <ColumnDefinition Width="*"/>
                <ColumnDefinition Width="Auto"/>
              </Grid.ColumnDefinitions>
              <StackPanel Orientation="Horizontal" VerticalAlignment="Center">
                <TextBlock Text="出力" FontSize="12" Foreground="{StaticResource Faint}" VerticalAlignment="Center" Margin="0,2,8,0"/>
                <TextBlock x:Name="OutputTitle" Text="Output" FontSize="16" FontWeight="Bold" VerticalAlignment="Center"/>
                <TextBlock x:Name="OutputStatus" FontSize="13" Margin="12,2,0,0" VerticalAlignment="Center"/>
              </StackPanel>
              <StackPanel Grid.Column="1" Orientation="Horizontal">
                <Button x:Name="StopButton" Style="{StaticResource Danger}" Visibility="Collapsed"
                        ToolTip="Interrompe lo script e i programmi che ha avviato">
                  <StackPanel Orientation="Horizontal">
                    <TextBlock Text="&#xE71A;" FontFamily="{StaticResource Icons}" VerticalAlignment="Center" Margin="0,0,8,0"/>
                    <TextBlock Text="Ferma"/>
                  </StackPanel>
                </Button>
                <Button x:Name="CopyOutputButton" Style="{StaticResource Small}" Content="Copia output"
                        ToolTip="Copia tutto l'output (Ctrl+C copia solo le righe selezionate)"/>
                <Button x:Name="ClearButton" Style="{StaticResource Small}" Content="Pulisci" Margin="0"
                        ToolTip="Toglie le esecuzioni finite" Visibility="Collapsed"/>
              </StackPanel>
            </Grid>
            <ListBox x:Name="RunTabs" DockPanel.Dock="Top" Margin="16,0,16,10" Visibility="Collapsed"
                     ItemContainerStyle="{StaticResource Chip}"
                     ScrollViewer.HorizontalScrollBarVisibility="Auto" ScrollViewer.VerticalScrollBarVisibility="Disabled">
              <ListBox.ItemsPanel>
                <ItemsPanelTemplate><StackPanel Orientation="Horizontal"/></ItemsPanelTemplate>
              </ListBox.ItemsPanel>
            </ListBox>
            <Grid DockPanel.Dock="Bottom" Margin="16,8,16,16">
              <Grid.ColumnDefinitions>
                <ColumnDefinition Width="*"/>
                <ColumnDefinition Width="Auto"/>
              </Grid.ColumnDefinitions>
              <TextBox x:Name="InputBox" FontFamily="Consolas" IsEnabled="False"/>
              <TextBlock x:Name="InputHint" Text="Nessuno script in corso" Foreground="{StaticResource Faint}"
                         Margin="11,0,0,0" VerticalAlignment="Center" IsHitTestVisible="False"/>
              <Button x:Name="SendButton" Grid.Column="1" Style="{StaticResource Small}" Margin="8,0,0,0" IsEnabled="False"
                      Content="Invia" ToolTip="Invia la risposta allo script (Invio)"/>
            </Grid>
            <Border Background="#0F1430" BorderBrush="{StaticResource Line}" BorderThickness="1.5" CornerRadius="12" Margin="16,0">
              <Grid>
                <ListBox x:Name="OutputList" SelectionMode="Extended" Padding="0,8"
                         ItemContainerStyle="{StaticResource OutputLine}"
                         VirtualizingPanel.IsVirtualizing="True" VirtualizingPanel.VirtualizationMode="Recycling">
                  <ListBox.ItemTemplate>
                    <DataTemplate>
                      <TextBlock Text="{Binding Text}" Foreground="{Binding Brush}" FontFamily="Consolas" FontSize="12.5"
                                 TextWrapping="Wrap" LineHeight="18"/>
                    </DataTemplate>
                  </ListBox.ItemTemplate>
                </ListBox>
                <StackPanel x:Name="EmptyOutput" HorizontalAlignment="Center" VerticalAlignment="Center">
                  <TextBlock Text="✦" Foreground="#F2C94C" FontSize="20" HorizontalAlignment="Center" Margin="0,0,0,8"/>
                  <TextBlock Foreground="{StaticResource Faint}" TextAlignment="Center" LineHeight="20"
                             Text="Premi Esegui: l'output dello script compare qui.&#x0a;Se lo script fa una domanda, rispondi nel campo in basso."/>
                </StackPanel>
              </Grid>
            </Border>
          </DockPanel>
        </Border>
      </Grid>
    </Grid>
  </Grid>
</Window>
'@

try {
    $window = [Windows.Markup.XamlReader]::Load((New-Object System.Xml.XmlNodeReader $xaml))
    foreach ($n in 'FilterBox', 'FilterHint', 'FolderButton', 'ReloadButton', 'ScriptList', 'NoMatch', 'NoScript',
        'Detail', 'DetailHeader', 'IconBox', 'GroupIcon', 'GroupText', 'TitleText', 'FileText', 'SynopsisText',
        'RunButton', 'ExternalButton', 'CopyButton', 'CopyText', 'EditButton', 'DetailScroll', 'ParamPanel',
        'DescriptionSection', 'DescriptionText', 'DescCaption', 'StepsSection', 'StepsPanel', 'StepsCaption',
        'OptionsPane', 'OptionsCaption', 'OutputTitle', 'OutputStatus', 'StopButton', 'CopyOutputButton',
        'ClearButton', 'RunTabs', 'InputBox', 'InputHint', 'SendButton', 'OutputList', 'EmptyOutput',
        'FontButton', 'FontPopup', 'FontPanel') {
        $control = $window.FindName($n)
        if (-not $control) { throw "Controllo $n non trovato nel layout." }
        Set-Variable -Name ($n.Substring(0, 1).ToLower() + $n.Substring(1)) -Value $control -Scope Script
    }
    $script:outputScroll = $null

    # Pulizia degli script di avvio rimasti (finestra chiusa durante un'esecuzione, console esterne).
    if (Test-Path -LiteralPath $tempDir) {
        Get-ChildItem -LiteralPath $tempDir -File | Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-1) } |
            Remove-Item -Force -ErrorAction SilentlyContinue
    }

    $window.Add_SourceInitialized({
        [AwNative]::DarkTitleBar((New-Object Windows.Interop.WindowInteropHelper $window).Handle)
    })
    $window.Add_Loaded({ $script:outputScroll = Find-ScrollViewer $outputList })
    $window.Add_Closing({
        param($src, $e)
        $active = @($script:runs | Where-Object { -not $_.Done })
        if (-not $active.Count) { return }
        $answer = [Windows.MessageBox]::Show($window,
            "Ci sono $($active.Count) script ancora in corso. Chiudendo la finestra si interrompono. Chiudere?",
            'Script in corso', 'YesNo', 'Warning')
        if ($answer -ne 'Yes') { $e.Cancel = $true; return }
        foreach ($run in $active) { Stop-Run $run }
    })
    $filterBox.Add_TextChanged({
        Invoke-Safe {
            $filterHint.Visibility = $(if ($filterBox.Text) { 'Collapsed' } else { 'Visible' })
            Update-List
        }
    })
    $scriptList.Add_SelectionChanged({
        Invoke-Safe {
            $item = $scriptList.SelectedItem
            if ($item -and $item.Tag -and $item.Tag -ne $script:current) { Show-Script $item.Tag }
        }
    })
    $runTabs.Add_SelectionChanged({
        Invoke-Safe {
            $item = $runTabs.SelectedItem
            if ($item -and $item.Tag -ne $script:selectedRun) { Select-Run $item.Tag }
        }
    })
    $runButton.Add_Click({ Invoke-Safe { Start-ScriptRun } })
    $externalButton.Add_Click({ Invoke-Safe { Start-ExternalRun } })
    $copyButton.Add_Click({
        Invoke-Safe {
            [Windows.Clipboard]::SetText($script:commandText)
            $copyText.Text = 'Copiato ✦'
            $script:copyTimer.Stop()
            $script:copyTimer.Start()
        }
    })
    $editButton.Add_Click({ Invoke-Safe { Open-Script } })
    $folderButton.Add_Click({ Invoke-Safe { Start-Process explorer.exe -ArgumentList ('"{0}"' -f $scriptsDir) } })
    $reloadButton.Add_Click({ Invoke-Safe { Import-Scripts } })
    # Un clic sul pulsante a pannello aperto lo chiude (StaysOpen="False") prima del Click: senza questo controllo il
    # Click lo riaprirebbe subito.
    $script:fontPopupClosed = [DateTime]::MinValue
    $fontPopup.Add_Closed({ $script:fontPopupClosed = [DateTime]::Now })
    $fontButton.Add_Click({
        Invoke-Safe {
            if (([DateTime]::Now - $script:fontPopupClosed).TotalMilliseconds -gt 300) { Show-FontPicker }
        }
    })
    $stopButton.Add_Click({ Invoke-Safe { Stop-Run $script:selectedRun } })
    $copyOutputButton.Add_Click({ Invoke-Safe { Copy-Output $false } })
    $clearButton.Add_Click({ Invoke-Safe { Clear-Runs } })
    $sendButton.Add_Click({ Invoke-Safe { Send-Input; [void]$inputBox.Focus() } })
    $inputBox.Add_KeyDown({
        param($src, $e)
        if ($e.Key -eq 'Return') { $e.Handled = $true; Invoke-Safe { Send-Input } }
    })
    $inputBox.Add_TextChanged({ $inputHint.Visibility = $(if ($inputBox.Text) { 'Collapsed' } else { 'Visible' }) })
    $outputList.Add_KeyDown({
        param($src, $e)
        $ctrl = [Windows.Input.Keyboard]::Modifiers -band [Windows.Input.ModifierKeys]::Control
        if ($e.Key -eq 'C' -and $ctrl) { $e.Handled = $true; Invoke-Safe { Copy-Output $true } }
    })
    $window.Add_PreviewKeyDown({
        param($src, $e)
        $ctrl = [Windows.Input.Keyboard]::Modifiers -band [Windows.Input.ModifierKeys]::Control
        if ($e.Key -eq 'F5') { $e.Handled = $true; Invoke-Safe { Import-Scripts } }
        elseif ($e.Key -eq 'Return' -and $ctrl) { $e.Handled = $true; Invoke-Safe { Start-ScriptRun } }
        elseif ($e.Key -eq 'F' -and $ctrl) { $e.Handled = $true; [void]$filterBox.Focus(); $filterBox.SelectAll() }
        elseif ($e.Key -eq 'Escape' -and $filterBox.IsKeyboardFocused -and $filterBox.Text) { $e.Handled = $true; $filterBox.Text = '' }
    })

    $script:copyTimer = New-Object Windows.Threading.DispatcherTimer
    $script:copyTimer.Interval = [TimeSpan]::FromSeconds(1.5)
    $script:copyTimer.Add_Tick({ $copyText.Text = 'Copia comando'; $script:copyTimer.Stop() })

    $timer = New-Object Windows.Threading.DispatcherTimer
    $timer.Interval = [TimeSpan]::FromMilliseconds(150)
    # Un errore qui si ripeterebbe a ogni giro: si ferma il timer e lo si mostra una volta.
    $timer.Add_Tick({
        try { Update-Runs } catch {
            $timer.Stop()
            [void][Windows.MessageBox]::Show("L'aggiornamento dell'output si è fermato:`n`n$($_.Exception.Message)`n$($_.InvocationInfo.PositionMessage)", 'Script', 'OK', 'Error')
        }
    })
    $timer.Start()

    Set-UiFont (Get-SavedFont) $false
    Import-Scripts
    Update-OutputHeader
    [void]$window.ShowDialog()
    $timer.Stop()
} catch {
    # La console è nascosta (gui.cmd): un errore all'avvio va mostrato in una finestra.
    [void][Windows.MessageBox]::Show("La finestra degli script non si è aperta:`n`n$($_.Exception.Message)`n$($_.InvocationInfo.PositionMessage)", 'Script', 'OK', 'Error')
    exit 1
}

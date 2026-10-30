# In-game test run (on the test box, started by tools/box-ingame.sh), in phases one after the other:
#   plain: a throwaway 1.12.2 server WITHOUT the mod (loopback, free port) - every test but the server ones;
#   forge: a throwaway Forge 1.12.2 dedicated server WITH the mod (installed by the Forge installer into the phase's own
#          folder) - the server/ tests;
#   optifine: the plain server again, OptiFine HD U G5 in the client mods (from _optifine/, never committed) - perf/optifine/.
# Each phase: a throwaway Prism instance with Forge + MixinBooter + the reobf'd mod jar + the selftest jar (never
# released) joins the server and runs the in-world tests (-Dlitematicaupgraded.selftest.phase picks the tests). The
# selftest prints SCOPE-TEST lines into the game log and "SCOPE-TEST done" at the end. Both logs are joined into
# _out/latest.log for tools/ingame_verdict.py; a phase that does not finish adds a failing line. Log + screenshots
# come back in _out/. Only what this run made is stopped/deleted. FILTER=<id prefix> runs only the phases it touches.
$ErrorActionPreference = 'Continue'
$here = Split-Path $PSScriptRoot -Parent
$out = Join-Path $here '_out'; New-Item -ItemType Directory -Force $out | Out-Null
$prism = 'E:\PrismLauncher\prismlauncher.exe'
$instRoot = 'E:\PrismLauncher\instances'
$limitMin = if ($env:INGAME_LIMIT_MIN) { [int]$env:INGAME_LIMIT_MIN } else { 15 }
$filter = $env:FILTER
$jars = @(Get-ChildItem "$here\build\libs\litematica_upgraded-*.jar" | Where-Object { $_.Name -notmatch 'sources|dev' })
if (-not ($jars | Where-Object Name -match 'selftest')) { 'INGAME-FAIL: no selftest jar (gradle selftestJar)'; exit 1 }
$toolchain = (Resolve-Path "$here\..\..\toolchain").Path
$java8 = Get-ChildItem "$toolchain\jdk8" -Recurse -Filter java.exe -EA 0 | Select -First 1 -Expand FullName
$forgeVersion = '1.12.2-14.23.5.2859'
# MixinBooter: the newest (what Prism downloads for players, 2026-10-29: 12.2) in the plain + forge phases, the oldest we
# support (11.18) in the OptiFine phase - both tested without an extra game launch
$mixinBooterNew = 'https://maven.cleanroommc.com/zone/rong/mixinbooter/12.2/mixinbooter-12.2.jar'
$mixinBooterOld = 'https://maven.cleanroommc.com/zone/rong/mixinbooter/11.18/mixinbooter-11.18.jar'
function MixinBooterUrl([string]$phase) { if ($phase -eq 'optifine') { return $mixinBooterOld } else { return $mixinBooterNew } }

Add-Type @'
using System; using System.Runtime.InteropServices; using System.Drawing; using System.Drawing.Imaging;
public static class W {
  [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr h, IntPtr dc, uint f);
  [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr h, out RECT r);
  public struct RECT { public int L, T, R, B; }
  public static void Shot(IntPtr h, string path) {
    RECT r; GetClientRect(h, out r); int w = Math.Max(1, r.R - r.L), ht = Math.Max(1, r.B - r.T);
    using (var bmp = new Bitmap(w, ht)) { using (var g = Graphics.FromImage(bmp)) { var dc = g.GetHdc(); PrintWindow(h, dc, 3); g.ReleaseHdc(dc); } bmp.Save(path, ImageFormat.Png); }
  }
}
'@ -ReferencedAssemblies System.Drawing

# Which phases the FILTER touches (no filter: both). server/fallback/ runs in the plain phase, the other server/ tests in the forge phase.
function Wants([string]$id) { return (-not $filter) -or $id.StartsWith($filter) -or $filter.StartsWith($id) }
$runPlain = (-not $filter) -or (-not $filter.StartsWith('server/')) -or (Wants 'server/fallback/') -or (Wants 'server/permission/fallback-')
$runForge = (-not $filter) -or (Wants 'server/')
# Phase 3 (short): the plain server again, with OptiFine in the client (tools/check-ingame.sh puts its jar in _optifine/; it is never committed).
$runOptifine = (-not $filter) -or (Wants 'perf/optifine/')
$optifineJar = Get-ChildItem "$here\_optifine\OptiFine_*.jar" -EA 0 | Select -First 1

# The test player's offline-mode uuid and name (whatever account Prism logs in with): op on the test servers, so tests can use /gamemode, /give, /setblock ...
$accounts = Get-Content 'E:\PrismLauncher\accounts.json' -Raw | ConvertFrom-Json
$player = @($accounts.accounts | Where-Object { $_.active } | % { $_.profile.name }) + @($accounts.accounts | % { $_.profile.name }) | Where-Object { $_ } | Select -First 1
$md5 = [System.Security.Cryptography.MD5]::Create().ComputeHash([System.Text.Encoding]::UTF8.GetBytes("OfflinePlayer:$player"))
$md5[6] = ($md5[6] -band 0x0f) -bor 0x30; $md5[8] = ($md5[8] -band 0x3f) -bor 0x80
$hex = -join ($md5 | % { $_.ToString('x2') })
$uuid = "$($hex.Substring(0,8))-$($hex.Substring(8,4))-$($hex.Substring(12,4))-$($hex.Substring(16,4))-$($hex.Substring(20,12))"

function Find-FreePort { foreach ($p in (Get-Random -Count 40 -InputObject (41000..48999))) { if (-not (Get-NetTCPConnection -LocalPort $p -EA 0)) { return $p } }; return 0 }

# Writes the files every throwaway server needs (eula, properties, ops).
function Write-ServerFiles([string]$srv, [int]$port) {
  "eula=true" | Set-Content "$srv\eula.txt" -Encoding ascii
  "online-mode=false`nserver-ip=127.0.0.1`nserver-port=$port`nlevel-type=FLAT`nspawn-protection=0`nmax-players=2`ngamemode=1`nspawn-monsters=false`nspawn-animals=false`nop-permission-level=4" | Set-Content "$srv\server.properties" -Encoding ascii
  "[{`"uuid`":`"$uuid`",`"name`":`"$player`",`"level`":4,`"bypassesPlayerLimit`":false}]" | Set-Content "$srv\ops.json" -Encoding ascii
}

# Phase 1's server: the Kisde carpet server jar, no mod.
function Start-PlainServer([string]$srv, [int]$port) {
  New-Item -ItemType Directory -Force $srv | Out-Null
  Copy-Item (Resolve-Path "$here\..\..\toolchain\kisde-carpet\*.jar" | Select -First 1) "$srv\server.jar"
  Write-ServerFiles $srv $port
  return Start-Process -FilePath $java8 -ArgumentList '-Xmx1G','-jar','server.jar','nogui' -WorkingDirectory $srv -WindowStyle Hidden -PassThru
}

# Phase 2's server: Forge installed by its installer into the phase's folder (the installer download is cached in the toolchain), our jar + MixinBooter in mods/.
function Start-ForgeServer([string]$srv, [int]$port) {
  New-Item -ItemType Directory -Force $srv | Out-Null
  $cache = Join-Path $toolchain 'forge-installer'; New-Item -ItemType Directory -Force $cache | Out-Null
  $installer = Join-Path $cache "forge-$forgeVersion-installer.jar"
  if (-not (Test-Path $installer)) {
    $part = "$installer.$([guid]::NewGuid().ToString('N')).part"
    Invoke-WebRequest "https://maven.minecraftforge.net/net/minecraftforge/forge/$forgeVersion/forge-$forgeVersion-installer.jar" -OutFile $part -UseBasicParsing
    if (-not (Test-Path $installer)) { Move-Item $part $installer } else { Remove-Item $part -Force }
  }
  Copy-Item $installer "$srv\installer.jar"
  Start-Process -FilePath $java8 -ArgumentList '-jar','installer.jar','--installServer' -WorkingDirectory $srv -WindowStyle Hidden -Wait -RedirectStandardOutput "$srv\install.out" -RedirectStandardError "$srv\install.err"
  Remove-Item "$srv\installer.jar" -Force -EA 0
  $universal = Get-ChildItem $srv -Filter 'forge-*.jar' | Where-Object { $_.Name -notmatch 'installer' } | Select -First 1
  if (-not $universal) { Write-Host "INGAME-FAIL: the Forge installer made no server jar (see install.out)"; Copy-Item "$srv\install.out","$srv\install.err" $out -EA 0; return $null }
  New-Item -ItemType Directory -Force "$srv\mods" | Out-Null
  $jars | Where-Object { $_.Name -notmatch 'selftest' } | % { Copy-Item $_.FullName "$srv\mods\" }
  Invoke-WebRequest $mixinBooterNew -OutFile "$srv\mods\mixinbooter.jar" -UseBasicParsing
  Write-ServerFiles $srv $port
  return Start-Process -FilePath $java8 -ArgumentList '-Xmx1G','-jar',$universal.Name,'nogui' -WorkingDirectory $srv -WindowStyle Hidden -PassThru
}

# One phase: server, then a client that joins it and runs the tests of the phase. Returns after cleaning up everything it started.
function Invoke-Phase([string]$phase) {
  $name = "zz-lu-ingame-$phase-" + (Get-Random -Maximum 99999)
  $prismBefore = @(Get-Process prismlauncher -EA 0 | Select -Expand Id)
  $dir = Join-Path $instRoot $name; $mc = Join-Path $dir '.minecraft'
  $srv = Join-Path $here "_server-$phase"
  $server = $null
  try {
    $port = Find-FreePort
    $server = if ($phase -eq 'forge') { Start-ForgeServer $srv $port } else { Start-PlainServer $srv $port }
    if ($phase -eq 'optifine' -and -not $optifineJar) { Write-Host "INGAME-FAIL: no OptiFine jar in _optifine/"; return }
    if (-not $server) { return }
    for ($i = 0; $i -lt 150 -and -not ((Test-Path "$srv\logs\latest.log") -and (Select-String "$srv\logs\latest.log" -Pattern 'Done \(' -Quiet)); $i++) { Start-Sleep 2 }
    "[$phase] server port $port"

    # client
    New-Item -ItemType Directory -Force "$mc\mods" | Out-Null
    $jars | % { Copy-Item $_.FullName "$mc\mods\" }
    if ($phase -eq 'optifine') { Copy-Item $optifineJar.FullName "$mc\mods\" }
    Invoke-WebRequest (MixinBooterUrl $phase) -OutFile "$mc\mods\mixinbooter.jar" -UseBasicParsing
    Copy-Item "$instRoot\1.12 testing\mmc-pack.json" "$dir\mmc-pack.json"
    $jvm = "-Dlitematicaupgraded.selftest=client -Dlitematicaupgraded.selftest.phase=$phase -Dlitematicaupgraded.selftest.join=127.0.0.1:$port"
    if ($env:FILTER) { $jvm += " -Dlitematicaupgraded.selftest.filter=$($env:FILTER)" }
    @"
InstanceType=OneSix
name=$name
OverrideJavaLocation=true
JavaPath=$($env:APPDATA -replace "\\", "/")/PrismLauncher/java/jre-legacy/bin/javaw.exe
OverrideMemory=true
MinMemAlloc=512
MaxMemAlloc=3072
OverrideJavaArgs=true
JvmArgs=$jvm
OverrideConsole=true
ShowConsole=false
"@ | Set-Content "$dir\instance.cfg" -Encoding ascii
    "soundCategory_master:0.0`npauseOnLostFocus:false`noverrideWidth:1024`noverrideHeight:576`nguiScale:2`nmaxFps:30`nrenderDistance:6`ntutorialStep:none" | Set-Content "$mc\options.txt" -Encoding ascii
    if ($phase -eq 'plain') {
      # import/: old config files of the mods players come from, there before the first start (src/selftest ImportTests):
      # LiteLoader Litematica (oldest), Schematica Upgraded (newer: its infoHudMaxLines wins), old Schematica's key in options.txt
      New-Item -ItemType Directory -Force "$mc\liteconfig\common", "$mc\config" | Out-Null
      '{"Generic":{"pasteCommandLimit":5000},"InfoOverlays":{"infoHudMaxLines":12,"materialListHudMaxLines":11},"Hotkeys":{"openGuiLoadedSchematics":"K"}}' | Set-Content "$mc\liteconfig\common\litematica.json" -Encoding ascii
      '{"hotkeys":{"openGuiSchematicProjects":{"keys":[36]}}}' | Set-Content "$mc\config\schematica_plus_hotkeys.json" -Encoding ascii
      "general {`n    I:infoHudMaxLines=14`n}" | Set-Content "$mc\config\schematica_plus.cfg" -Encoding ascii
      (Get-Item "$mc\liteconfig\common\litematica.json").LastWriteTime = (Get-Date).AddDays(-2)
      (Get-Item "$mc\config\schematica_plus_hotkeys.json").LastWriteTime = (Get-Date).AddDays(-1)
      (Get-Item "$mc\config\schematica_plus.cfg").LastWriteTime = (Get-Date).AddDays(-1)
      "key_schematica.key.load:35" | Add-Content "$mc\options.txt" -Encoding ascii
    }
    Start-Process -FilePath $prism -ArgumentList '-l', $name, '-s', "127.0.0.1:$port"
    $log = "$mc\logs\latest.log"; $game = $null; $done = $false; $deadline = (Get-Date).AddMinutes($limitMin); $i = 0
    while ((Get-Date) -lt $deadline) {
      Start-Sleep 3; $i++
      $game = Get-CimInstance Win32_Process -Filter "Name like 'java%'" | Where-Object { [string]$_.CommandLine -like "*$name*" } | Select -First 1
      if ((Test-Path $log) -and (Select-String $log -Pattern 'SCOPE-TEST done' -Quiet)) { $done = $true; break }
      if (Get-ChildItem "$mc\crash-reports" -EA 0) { break }
      if ($i -gt 60 -and -not $game) { break }
      if ($i % 20 -eq 0) { $n = if (Test-Path $log) { @(Select-String $log -Pattern 'SCOPE-TEST (pass|fail|skip) ').Count } else { 0 }; "  [$phase] running $([int]($i * 3))s, $n results" }
    }
    if ($game -and -not $done) { [W]::Shot((Get-Process -Id $game.ProcessId).MainWindowHandle, "$out\stuck-$phase.png") }
    "[$phase] finished=$done crashReports=$(@(Get-ChildItem "$mc\crash-reports" -EA 0).Count)"
    Get-ChildItem "$mc\crash-reports" -EA 0 | % { Copy-Item $_.FullName "$out\" }
    if (Test-Path "$mc\selftest-shots") { Copy-Item "$mc\selftest-shots\*" "$out\" -EA 0 }
    $script:phaseDone[$phase] = $done
  } finally {
    Get-CimInstance Win32_Process -Filter "Name like 'java%'" | Where-Object { [string]$_.CommandLine -like "*$name*" } | % { Stop-Process -Id $_.ProcessId -Force -EA 0 }
    Get-Process prismlauncher -EA 0 | Where-Object { $prismBefore -notcontains $_.Id } | Stop-Process -Force -EA 0
    if ($server) { Stop-Process -Id $server.Id -Force -EA 0 }
    Start-Sleep 3
    if (Test-Path "$mc\logs\latest.log") { Copy-Item "$mc\logs\latest.log" "$out\latest-$phase.log" -EA 0 }
    if (Test-Path "$srv\logs\latest.log") { Copy-Item "$srv\logs\latest.log" "$out\server-$phase.log" -EA 0 }
    if ($phase -eq 'forge' -and (Test-Path "$srv\config\litematica_upgraded-server.cfg")) { Copy-Item "$srv\config\litematica_upgraded-server.cfg" "$out\server-forge.cfg" -EA 0 }
    Remove-Item $dir -Recurse -Force -EA 0
    Remove-Item $srv -Recurse -Force -EA 0
  }
}

$script:phaseDone = @{}
if ($runPlain) { Invoke-Phase 'plain' }
if ($runForge) { Invoke-Phase 'forge' }
if ($runOptifine) { Invoke-Phase 'optifine' }

# One log for the verdict: the plain phase, then the forge phase, then the lines only this script can judge.
$extra = New-Object System.Collections.Generic.List[string]
foreach ($phase in @('plain', 'forge', 'optifine')) {
  if (($phase -eq 'plain' -and $runPlain) -or ($phase -eq 'forge' -and $runForge) -or ($phase -eq 'optifine' -and $runOptifine)) {
    if (-not $script:phaseDone[$phase]) { $extra.Add("SCOPE-TEST fail harness/phase-$phase :: the $phase phase did not finish") }
  }
}
if ($runForge) {
  $serverLog = if (Test-Path "$out\server-forge.log") { Get-Content "$out\server-forge.log" -Raw } else { '' }
  if (Wants 'server/load/dedicated-server-loads-the-mod-without-client-classes') {
    $bad = [regex]::Match($serverLog, 'NoClassDefFoundError|ClassNotFoundException|Mixin apply failed|Exception in thread|Encountered an unexpected exception|Missing Mods|\[ERROR\].*litematica')
    $ready = $serverLog -match 'Litematica Upgraded ready'
    $started = $serverLog -match 'Done \('
    $badText = $bad.Value
    if ($ready -and $started -and -not $bad.Success) {
      $extra.Add('SCOPE-TEST pass server/load/dedicated-server-loads-the-mod-without-client-classes')
    } else {
      $extra.Add('SCOPE-TEST fail server/load/dedicated-server-loads-the-mod-without-client-classes :: server log: ready=' + $ready + ' done=' + $started + ' bad=' + $badText)
    }
  }
  if (Wants 'server/admin/change-is-saved-to-the-config-file') {
    # the last admin test switches the printer off as an operator, then a de-opped player tries to switch it on: the saved file must say off
    $cfg = if (Test-Path "$out\server-forge.cfg") { Get-Content "$out\server-forge.cfg" -Raw } else { '' }
    if ($cfg -match 'printerAllowed\s*=\s*false') { $extra.Add('SCOPE-TEST pass server/admin/change-is-saved-to-the-config-file') }
    else { $extra.Add('SCOPE-TEST fail server/admin/change-is-saved-to-the-config-file :: the server config file does not say printerAllowed=false') }
  }
}
$parts = @()
foreach ($phase in @('plain', 'forge', 'optifine')) { if (Test-Path "$out\latest-$phase.log") { $parts += (Get-Content "$out\latest-$phase.log" -Raw -Encoding UTF8) } }
($parts + ($extra -join "`n")) -join "`n" | Set-Content "$out\latest.log" -Encoding UTF8
if ($extra.Count) { $extra }
'INGAME-END'

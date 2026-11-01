# In-game test run (on the test box, started by tools/box-ingame.sh), in phases one after the other:
#   plain: a throwaway 1.12.2 server WITHOUT the mod (loopback, free port) - every test but the server ones;
#   forge: a throwaway Forge 1.12.2 dedicated server WITH the mod (installed by the Forge installer into the phase's own
#          folder) - the server/ tests;
#   optifine: the plain server again, OptiFine HD U G5 in the client mods (from _optifine/, never committed) - perf/optifine/.
#   liteloader: the plain server again, the client = Forge + LiteLoader (Prism component) and NO MixinBooter, masa's LiteLoader
#               Litematica, malilib, MiniHUD, Tweakeroo, Item Scroller installed too - compat/liteloader/.
#   liteloader-first: a player's setup (Discord #2): the plain server again, the client starts LiteLoader BEFORE Forge, OptiFine
#               in mods/1.12.2, masa's malilib, MiniHUD, Tweakeroo, Item Scroller (not his Litematica), no MixinBooter -
#               compat/liteloader-first/.
#   liteloader-optifine: a player's setup (Discord #4): the plain server again, the client = Forge first, LiteLoader after it, OptiFine
#               in mods/1.12.2, masa's malilib, MiniHUD, Tweakeroo, Item Scroller (not his Litematica), no MixinBooter; the client must
#               keep ticking and drawing after joining - compat/liteloader-optifine/.
#   liteloader-alone: the plain server again, the client = Forge + LiteLoader, none of masa's mods, no OptiFine, no MixinBooter
#               - compat/liteloader-alone/.
# Each phase: a throwaway Prism instance with Forge + MixinBooter (not in the liteloader phase) + the reobf'd mod jar + the selftest jar (never
# released) joins the server and runs the in-world tests (-Dlitematicaupgraded.selftest.phase picks the tests). The
# selftest prints SCOPE-TEST lines into the game log and "SCOPE-TEST done" at the end. Both logs are joined into
# _out/latest.log for tools/ingame_verdict.py; a phase that does not finish adds a failing line. Log + screenshots
# come back in _out/. Only what this run made is stopped/deleted. FILTER=<id prefix> runs only the phases it touches.
$ErrorActionPreference = 'Continue'
$here = Split-Path $PSScriptRoot -Parent
$out = Join-Path $here '_out'; New-Item -ItemType Directory -Force $out | Out-Null
# Prism: portable on E: on the test box; on this PC (--here) installed elsewhere with its data in %APPDATA%
$prismData = if (Test-Path 'E:\PrismLauncher\accounts.json') { 'E:\PrismLauncher' } else { "$env:APPDATA\PrismLauncher" }
$prism = @("$prismData\prismlauncher.exe", (Get-Process prismlauncher -EA 0 | Select -First 1 -Expand Path), 'D:\PrismLauncher\prismlauncher.exe',
           "$env:LOCALAPPDATA\Programs\PrismLauncher\prismlauncher.exe") | Where-Object { $_ -and (Test-Path $_) } | Select -First 1
if (-not $prism) { 'INGAME-FAIL: Prism Launcher not found'; exit 1 }
$instRoot = Join-Path $prismData 'instances'
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
# masa's own LiteLoader mods next to ours (Discord #2): players keep malilib, MiniHUD, Tweakeroo, Item Scroller; his Litematica,
# which ours replaces, is there too: the game must run and tell the player to remove it
$masaLitemods = @('litematica/litematica-liteloader-1.12.2-0.31.4', 'malilib/malilib-liteloader-1.12.2-0.54.0', 'minihud/minihud-liteloader-1.12.2-0.41.1',
                  'tweakeroo/tweakeroo-liteloader-1.12.2-0.31.0', 'itemscroller/itemscroller-liteloader-1.12.2-0.31.1') | % { "https://masa.dy.fi/mcmods/$_.litemod" }
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
# FILTER may list several prefixes, comma separated
$filters = @("$filter".Split(',') | Where-Object { $_ })
function Wants([string]$id) { return (-not $filter) -or @($filters | Where-Object { $id.StartsWith($_) -or $_.StartsWith($id) }).Count -gt 0 }
$runPlain = (-not $filter) -or @($filters | Where-Object { -not $_.StartsWith('server/') -and -not $_.StartsWith('compat/liteloader/') -and -not $_.StartsWith('compat/liteloader-first/') -and -not $_.StartsWith('compat/liteloader-alone/') -and -not $_.StartsWith('compat/liteloader-optifine/') }).Count -gt 0 -or (Wants 'server/fallback/') -or (Wants 'server/permission/fallback-')
$runForge = (-not $filter) -or (Wants 'server/')
# Phase 3 (short): the plain server again, with OptiFine in the client (tools/check-ingame.sh puts its jar in _optifine/; it is never committed).
$runOptifine = (-not $filter) -or (Wants 'perf/optifine/')
# Phase 4 (short): the plain server again, the client runs Forge + LiteLoader and has NO MixinBooter (LiteLoader's own Mixin).
$runLiteloader = (-not $filter) -or (Wants 'compat/liteloader/')
$runLiteloaderFirst = (-not $filter) -or (Wants 'compat/liteloader-first/')
$runLiteloaderAlone = (-not $filter) -or (Wants 'compat/liteloader-alone/')
$runLiteloaderOptifine = (-not $filter) -or (Wants 'compat/liteloader-optifine/')
$optifineJar = Get-ChildItem "$here\_optifine\OptiFine_*.jar" -EA 0 | Select -First 1

# The test player's offline-mode uuid and name (whatever account Prism logs in with): op on the test servers, so tests can use /gamemode, /give, /setblock ...
$accounts = Get-Content "$prismData\accounts.json" -Raw | ConvertFrom-Json
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
  # built next to the instances folder and moved in whole just before launch: an open Prism looks at a new folder once,
  # the moment it appears, and never takes it as an instance if its files come later
  $dir = Join-Path $prismData "zz-staging-$name"; $mc = Join-Path $dir '.minecraft'
  $srv = Join-Path $here "_server-$phase"
  $server = $null
  try {
    $port = Find-FreePort
    $server = if ($phase -eq 'forge') { Start-ForgeServer $srv $port } else { Start-PlainServer $srv $port }
    if (($phase -in @('optifine', 'liteloader-first', 'liteloader-optifine')) -and -not $optifineJar) { Write-Host "INGAME-FAIL: no OptiFine jar in _optifine/"; return }
    if (-not $server) { return }
    for ($i = 0; $i -lt 150 -and -not ((Test-Path "$srv\logs\latest.log") -and (Select-String "$srv\logs\latest.log" -Pattern 'Done \(' -Quiet)); $i++) { Start-Sleep 2 }
    "[$phase] server port $port"

    # client
    New-Item -ItemType Directory -Force "$mc\mods" | Out-Null
    $jars | % { Copy-Item $_.FullName "$mc\mods\" }
    if ($phase -eq 'optifine') { Copy-Item $optifineJar.FullName "$mc\mods\" }
    if ($phase -in @('liteloader-first', 'liteloader-optifine')) { New-Item -ItemType Directory -Force "$mc\mods\1.12.2" | Out-Null; Copy-Item $optifineJar.FullName "$mc\mods\1.12.2\" }
    if ($phase -notlike 'liteloader*') { Invoke-WebRequest (MixinBooterUrl $phase) -OutFile "$mc\mods\mixinbooter.jar" -UseBasicParsing }
    Copy-Item "$instRoot\1.12 testing\mmc-pack.json" "$dir\mmc-pack.json"
    if ($phase -like 'liteloader*') {
      # LiteLoader as a Prism component after Forge (Prism fetches it into its own cache; its Mixin is the only one in the game)
      $pack = Get-Content "$dir\mmc-pack.json" -Raw | ConvertFrom-Json
      $liteLoader = [pscustomobject]@{ cachedName = 'LiteLoader'; cachedVersion = '1.12.2-SNAPSHOT'; uid = 'com.mumfrey.liteloader'; version = '1.12.2-SNAPSHOT' }
      # listed before Forge, Prism starts LiteLoader first (as some players have it); after Forge, Forge starts first
      $forgeAt = [array]::IndexOf(@($pack.components | % { $_.uid }), 'net.minecraftforge')
      $pack.components = if ($phase -eq 'liteloader-first') { @($pack.components[0..($forgeAt - 1)]) + $liteLoader + @($pack.components[$forgeAt..($pack.components.Count - 1)]) } else { @($pack.components) + $liteLoader }
      $pack | ConvertTo-Json -Depth 10 | Set-Content "$dir\mmc-pack.json" -Encoding ascii
      foreach ($url in $masaLitemods | Where-Object { $phase -eq 'liteloader' -or ($phase -in @('liteloader-first', 'liteloader-optifine') -and $_ -notlike '*/litematica-liteloader-*') }) { Invoke-WebRequest $url -OutFile "$mc\mods\$(Split-Path $url -Leaf)" -UseBasicParsing }
    }
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
    Move-Item $dir (Join-Path $instRoot $name); $dir = Join-Path $instRoot $name; $mc = Join-Path $dir '.minecraft'
    Start-Process -FilePath $prism -ArgumentList '-l', $name, '-s', "127.0.0.1:$port"
    $log = "$mc\logs\latest.log"; $game = $null; $done = $false; $deadline = (Get-Date).AddMinutes($limitMin); $i = 0
    while ((Get-Date) -lt $deadline) {
      Start-Sleep 3; $i++
      $game = Get-CimInstance Win32_Process -Filter "Name like 'java%'" | Where-Object { [string]$_.CommandLine -like "*$name*" } | Select -First 1
      if ((Test-Path $log) -and (Select-String $log -Pattern 'SCOPE-TEST done' -Quiet)) { $done = $true; break }
      if (Get-ChildItem "$mc\crash-reports" -EA 0) { break }
      # a Prism that was already open (this PC, --here) may not know the new instance yet and ignores the launch: ask again
      if ($i % 5 -eq 0 -and -not (Select-String "$dir\instance.cfg" -Pattern '^lastLaunchTime=' -Quiet)) { Start-Process -FilePath $prism -ArgumentList '-l', $name, '-s', "127.0.0.1:$port" }
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
if ($runLiteloader) { Invoke-Phase 'liteloader' }
if ($runLiteloaderFirst) { Invoke-Phase 'liteloader-first' }
if ($runLiteloaderAlone) { Invoke-Phase 'liteloader-alone' }
if ($runLiteloaderOptifine) { Invoke-Phase 'liteloader-optifine' }

# One log for the verdict: the plain phase, then the forge phase, then the lines only this script can judge.
$extra = New-Object System.Collections.Generic.List[string]
foreach ($phase in @('plain', 'forge', 'optifine', 'liteloader', 'liteloader-first', 'liteloader-alone', 'liteloader-optifine')) {
  if (($phase -eq 'plain' -and $runPlain) -or ($phase -eq 'forge' -and $runForge) -or ($phase -eq 'optifine' -and $runOptifine) -or ($phase -eq 'liteloader' -and $runLiteloader) -or ($phase -eq 'liteloader-first' -and $runLiteloaderFirst) -or ($phase -eq 'liteloader-alone' -and $runLiteloaderAlone) -or ($phase -eq 'liteloader-optifine' -and $runLiteloaderOptifine)) {
    if (-not $script:phaseDone[$phase]) { $extra.Add("SCOPE-TEST fail harness/phase-$phase :: the $phase phase did not finish") }
  }
}
if ($runLiteloaderFirst -and (Wants 'compat/liteloader-first/liteloader-started-first')) {
  # the point of the phase: Prism started LiteLoader before Forge (its tweaker is the game's primary one)
  $first = (Test-Path "$out\latest-liteloader-first.log") -and (Select-String "$out\latest-liteloader-first.log" -Pattern 'Using primary tweak class name com.mumfrey.liteloader.launch.LiteLoaderTweaker' -SimpleMatch -Quiet)
  $extra.Add($(if ($first) { 'SCOPE-TEST pass compat/liteloader-first/liteloader-started-first' } else { 'SCOPE-TEST fail compat/liteloader-first/liteloader-started-first :: the game log does not show LiteLoader as the primary tweaker' }))
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
foreach ($phase in @('plain', 'forge', 'optifine', 'liteloader', 'liteloader-first', 'liteloader-alone', 'liteloader-optifine')) { if (Test-Path "$out\latest-$phase.log") { $parts += (Get-Content "$out\latest-$phase.log" -Raw -Encoding UTF8) } }
($parts + ($extra -join "`n")) -join "`n" | Set-Content "$out\latest.log" -Encoding UTF8
if ($extra.Count) { $extra }
'INGAME-END'

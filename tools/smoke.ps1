# Production smoke test (runs on the test box, started by tools/box-smoke.sh): a throwaway Prism instance with Forge
# 1.12.2 + MixinBooter + the reobf'd jar must reach the main menu with every mixin config loaded and no mixin error.
# Prints SMOKE-PASS / SMOKE-FAIL; leaves a screenshot in _out/. Only the processes and instance it made are stopped.
$ErrorActionPreference = 'Continue'
$here = Split-Path $PSScriptRoot -Parent
$out = Join-Path $here '_out'; New-Item -ItemType Directory -Force $out | Out-Null
$prism = 'E:\PrismLauncher\prismlauncher.exe'
$instRoot = 'E:\PrismLauncher\instances'
$name = 'zz-lu-smoke-' + (Get-Random -Maximum 99999)
$prismBefore = @(Get-Process prismlauncher -EA 0 | Select -Expand Id)
$jar = Get-ChildItem "$here\build\libs\litematica_upgraded-*.jar" | Where-Object { $_.Name -notmatch 'sources|dev' } | Select -First 1
if (-not $jar) { 'SMOKE-FAIL: no built jar'; exit 1 }

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

$dir = Join-Path $instRoot $name; $mc = Join-Path $dir '.minecraft'
$result = 'SMOKE-FAIL: did not finish'
try {
  New-Item -ItemType Directory -Force "$mc\mods" | Out-Null
  Copy-Item $jar.FullName "$mc\mods\"
  Invoke-WebRequest 'https://maven.cleanroommc.com/zone/rong/mixinbooter/11.18/mixinbooter-11.18.jar' -OutFile "$mc\mods\mixinbooter-11.18.jar" -UseBasicParsing
  Copy-Item "$instRoot\1.12 testing\mmc-pack.json" "$dir\mmc-pack.json"
  @"
InstanceType=OneSix
name=$name
OverrideJavaLocation=true
JavaPath=$($env:APPDATA -replace "\\", "/")/PrismLauncher/java/jre-legacy/bin/javaw.exe
OverrideMemory=true
MinMemAlloc=512
MaxMemAlloc=3072
OverrideJavaArgs=true
JvmArgs=-Dmixin.debug.verbose=true
OverrideConsole=true
ShowConsole=false
"@ | Set-Content "$dir\instance.cfg" -Encoding ascii
  "soundCategory_master:0.0`npauseOnLostFocus:false`noverrideWidth:854`noverrideHeight:480`nmaxFps:30" | Set-Content "$mc\options.txt" -Encoding ascii
  Start-Process -FilePath $prism -ArgumentList '-l', $name
  $log = "$mc\logs\latest.log"; $game = $null; $ready = $false
  for ($i = 0; $i -lt 200; $i++) {
    Start-Sleep 3
    if ($i % 10 -eq 0) { "  waiting $($i * 3)s" }
    $game = Get-CimInstance Win32_Process -Filter "Name like 'java%'" | Where-Object { [string]$_.CommandLine -like "*$name*" } | Select -First 1
    if (Get-ChildItem "$mc\crash-reports" -EA 0) { break }
    if ($i -gt 60 -and -not $game) { break }
    if ((Test-Path $log) -and (Select-String $log -Pattern 'Sound engine started' -Quiet -EA 0)) { $ready = $true; break }
  }
  if ($ready) { Start-Sleep 20; $game = Get-CimInstance Win32_Process -Filter "Name like 'java%'" | Where-Object { [string]$_.CommandLine -like "*$name*" } | Select -First 1 }
  if (-not (Test-Path $log)) { New-Item -ItemType File -Force $log | Out-Null }
  $errors = @(Select-String $log -Pattern 'MixinApplyError|InvalidInjectionException|FAILED during APPLY|Mixin apply failed|was not found in the target|could not find any targets|Critical injection failure|ClassNotFoundException: com\.useleess' -EA 0)
  $configs = @(Select-String $log -Pattern 'mixins\.(litematica|malilib)\.json' -EA 0).Count
  $mixed = @(Select-String $log -Pattern 'Mixing .* from mixins\.(litematica|malilib)\.json' -EA 0).Count
  $init = Select-String $log -Pattern 'Litematica Upgraded ready' -Quiet -EA 0
  $crash = @(Get-ChildItem "$mc\crash-reports" -EA 0).Count
  "menu=$ready alive=$([bool]$game) crashReports=$crash mixinErrors=$($errors.Count) configLines=$configs mixedClasses=$mixed initLine=$init"
  $errors | Select -First 15 | % { '   ' + $_.Line.Substring(0, [Math]::Min(250, $_.Line.Length)) }
  Get-ChildItem "$mc\crash-reports" -EA 0 | % { Get-Content $_.FullName -TotalCount 40 | % { '   | ' + $_ } }
  if ($game) { [W]::Shot((Get-Process -Id $game.ProcessId).MainWindowHandle, "$out\main-menu.png") }
  if ($ready -and $game -and $crash -eq 0 -and $errors.Count -eq 0 -and $mixed -gt 0 -and $init) { $result = 'SMOKE-PASS' }
  else { $result = 'SMOKE-FAIL' }
} finally {
  Get-CimInstance Win32_Process -Filter "Name like 'java%'" | Where-Object { [string]$_.CommandLine -like "*$name*" } | % { Stop-Process -Id $_.ProcessId -Force -EA 0 }
  Get-Process prismlauncher -EA 0 | Where-Object { $prismBefore -notcontains $_.Id } | Stop-Process -Force -EA 0
  Start-Sleep 3
  if (Test-Path "$mc\logs\latest.log") { Copy-Item "$mc\logs\latest.log" "$out\latest.log" -EA 0 }
  Remove-Item $dir -Recurse -Force -EA 0
}
$result

param(
    [Parameter(Mandatory=$true)][string]$ApkPath,
    [Parameter(Mandatory=$true)][string]$SdkRoot,
    [string]$OutDir=(Join-Path $PSScriptRoot 'build\package')
)
$ErrorActionPreference='Stop'
$apk=(Resolve-Path -LiteralPath $ApkPath).Path
$sdk=(Resolve-Path -LiteralPath $SdkRoot).Path
$out=[IO.Path]::GetFullPath($OutDir)
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$aapt=Join-Path $sdk 'build-tools\36.0.0\aapt.exe'
$badging=(& $aapt dump badging $apk) -join "`n"
if($LASTEXITCODE -ne 0){throw 'Could not inspect the bundled APK.'}
$package=[regex]::Match($badging,"package: name='app.tidecard' versionCode='(\d+)' versionName='([^']+)'")
$code=$package.Groups[1].Value
$version=$package.Groups[2].Value
if($code -ne '13' -or $version -ne '2.4.2'){throw 'Review desktop protocol and package version before changing the bundled version.'}
$name="TideCard-$version.apk"
$hash=(Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
New-Item -ItemType Directory -Path $out -Force | Out-Null
$build=Join-Path $PSScriptRoot 'build'
New-Item -ItemType Directory -Path $build -Force | Out-Null
$generated=Join-Path $build 'BundleInfo.g.cs'
$source="namespace TideCardUsb { internal static class BundleInfo { internal const int VersionCode=$code; internal const string VersionName=`"$version`", ApkName=`"$name`", ApkSha256=`"$hash`"; } }"
[IO.File]::WriteAllText($generated,$source,[Text.UTF8Encoding]::new($false))
$csc=Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
if(-not(Test-Path -LiteralPath $csc)){throw '.NET Framework C# compiler not found.'}
$exe=Join-Path $out 'TideCard-USB.exe'
$common=@('/nologo','/optimize+','/utf8output','/platform:anycpu','/r:System.Windows.Forms.dll','/r:System.Drawing.dll','/r:System.Core.dll','/r:System.Management.dll','/r:System.Web.Extensions.dll','/r:System.IO.Compression.dll','/r:System.IO.Compression.FileSystem.dll')
$sources=@((Join-Path $PSScriptRoot 'Updates.cs'),(Join-Path $PSScriptRoot 'UpdateDialog.cs'),(Join-Path $PSScriptRoot 'Core.cs'),(Join-Path $PSScriptRoot 'Program.cs'),(Join-Path $PSScriptRoot 'AssemblyInfo.cs'),(Join-Path $PSScriptRoot 'AdbDiagnostics.cs'),(Join-Path $PSScriptRoot 'LocalAdbServerProbe.cs'),(Join-Path $PSScriptRoot 'ConnectionDiagnostics.cs'),(Join-Path $PSScriptRoot 'DiagnosticDialog.cs'),$generated)
& $csc @common /target:winexe /main:TideCardUsb.Program "/win32manifest:$(Join-Path $PSScriptRoot 'app.manifest')" "/out:$exe" @sources
if($LASTEXITCODE -ne 0){throw 'Desktop compiler failed.'}
$checks=Join-Path $build 'UsbActivator.Checks.exe'
& $csc @common /target:exe /main:TideCardUsb.Checks "/win32manifest:$(Join-Path $PSScriptRoot 'app.manifest')" "/out:$checks" @sources (Join-Path $PSScriptRoot 'MenuLifecycleChecks.cs') (Join-Path $PSScriptRoot 'UpdateChecks.cs') (Join-Path $PSScriptRoot 'Checks.cs') (Join-Path $PSScriptRoot 'LayoutChecks.cs') (Join-Path $PSScriptRoot 'ConnectionDiagnosticsChecks.cs')
if($LASTEXITCODE -ne 0){throw 'Desktop checks compiler failed.'}
Copy-Item -LiteralPath $apk -Destination (Join-Path $out $name) -Force
$adbOut=Join-Path $out 'adb'
New-Item -ItemType Directory -Path $adbOut -Force | Out-Null
foreach($file in @('adb.exe','AdbWinApi.dll','AdbWinUsbApi.dll','NOTICE.txt','source.properties')){
    $source=Join-Path $sdk "platform-tools\$file"; $destination=Join-Path $adbOut $file
    if((Test-Path -LiteralPath $destination) -and (Get-FileHash -LiteralPath $source).Hash -eq (Get-FileHash -LiteralPath $destination).Hash){continue}
    Copy-Item -LiteralPath $source -Destination $destination -Force
}
Copy-Item -LiteralPath (Join-Path $repo 'LICENSE') -Destination (Join-Path $out 'LICENSE.txt') -Force
Copy-Item -LiteralPath (Join-Path $PSScriptRoot '使用说明.txt') -Destination $out -Force
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'App.config') -Destination (Join-Path $out 'TideCard-USB.exe.config') -Force
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'App.config') -Destination "$checks.config" -Force
& $checks (Join-Path $out $name) (Join-Path $build 'desktop-preview.png') (Join-Path $adbOut 'adb.exe')
if($LASTEXITCODE -ne 0){throw 'Desktop checks failed.'}
$sums=Get-ChildItem -LiteralPath $out -File -Recurse | Where-Object {$_.Name -ne 'SHA256SUMS.txt'} | Sort-Object FullName | ForEach-Object { '{0}  {1}' -f (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant(),$_.FullName.Substring($out.Length+1).Replace('\','/') }
[IO.File]::WriteAllLines((Join-Path $out 'SHA256SUMS.txt'),$sums,[Text.UTF8Encoding]::new($false))
Write-Output "Built USB activation package: $out"
Write-Output "APK SHA256: $hash"

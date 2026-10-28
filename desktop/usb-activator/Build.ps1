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
$gradle=[IO.File]::ReadAllText((Join-Path $repo 'app\build.gradle'))
$code=[regex]::Match($gradle,'versionCode\s+(\d+)').Groups[1].Value
$version=[regex]::Match($gradle,"versionName\s+'([^']+)'").Groups[1].Value
if($code -ne '8' -or $version -ne '2.3.0-rc2'){throw 'Review desktop protocol and package version before changing the bundled version.'}
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
$common=@('/nologo','/optimize+','/utf8output','/platform:anycpu','/r:System.Windows.Forms.dll','/r:System.Drawing.dll','/r:System.Core.dll')
$sources=@((Join-Path $PSScriptRoot 'Core.cs'),(Join-Path $PSScriptRoot 'Program.cs'),(Join-Path $PSScriptRoot 'AssemblyInfo.cs'),$generated)
& $csc @common /target:winexe /main:TideCardUsb.Program "/win32manifest:$(Join-Path $PSScriptRoot 'app.manifest')" "/out:$exe" @sources
if($LASTEXITCODE -ne 0){throw 'Desktop compiler failed.'}
$checks=Join-Path $build 'UsbActivator.Checks.exe'
& $csc @common /target:exe /main:TideCardUsb.Checks "/win32manifest:$(Join-Path $PSScriptRoot 'app.manifest')" "/out:$checks" @sources (Join-Path $PSScriptRoot 'Checks.cs') (Join-Path $PSScriptRoot 'LayoutChecks.cs')
if($LASTEXITCODE -ne 0){throw 'Desktop checks compiler failed.'}
Copy-Item -LiteralPath $apk -Destination (Join-Path $out $name) -Force
$adbOut=Join-Path $out 'adb'
New-Item -ItemType Directory -Path $adbOut -Force | Out-Null
foreach($file in @('adb.exe','AdbWinApi.dll','AdbWinUsbApi.dll','NOTICE.txt','source.properties')){Copy-Item -LiteralPath (Join-Path $sdk "platform-tools\$file") -Destination (Join-Path $adbOut $file) -Force}
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

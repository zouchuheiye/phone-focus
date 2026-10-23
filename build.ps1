param([string]$ToolchainRoot = (Join-Path $env:USERPROFILE 'Documents\Codex\2026-10-13\codex-v0-1-android-ai-android\work\toolchain'), [switch]$Online)
$ErrorActionPreference = 'Stop'
$taskGradleConfig = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'app\build.gradle') -Raw
if ($taskGradleConfig -notmatch "versionName '([0-9]+\.[0-9]+\.[0-9]+)'") { throw 'Missing release version' }
$taskVersion = $Matches[1]
$env:JAVA_HOME = Join-Path $ToolchainRoot 'zulu17.68.17-ca-jdk17.0.20-win_x64'
$env:ANDROID_HOME = Join-Path $ToolchainRoot 'android-sdk'
$gradleBin = Join-Path $ToolchainRoot 'gradle-8.13\bin\gradle.bat'
foreach ($path in @($env:JAVA_HOME, $env:ANDROID_HOME, $gradleBin)) {
    if (-not (Test-Path -LiteralPath $path)) { throw "Missing toolchain: $path" }
}
$privateDir = Join-Path $PSScriptRoot '.local'
New-Item -ItemType Directory -Path $privateDir -Force | Out-Null
$secretPath = Join-Path $privateDir 'signing.json'
if (-not (Test-Path -LiteralPath $secretPath)) {
    $bytes = New-Object byte[] 24
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    $secret = [BitConverter]::ToString($bytes).Replace('-','').ToLowerInvariant()
    @{ password = $secret } | ConvertTo-Json | Set-Content -LiteralPath $secretPath -Encoding UTF8
}
$env:PHONEFOCUS_SIGNING_PASSWORD = (Get-Content -LiteralPath $secretPath -Raw | ConvertFrom-Json).password
$env:PHONEFOCUS_KEYSTORE = Join-Path $privateDir 'phone-focus-release.jks'
try {
    if (-not (Test-Path -LiteralPath $env:PHONEFOCUS_KEYSTORE)) {
        & (Join-Path $env:JAVA_HOME 'bin\keytool.exe') -genkeypair -keystore $env:PHONEFOCUS_KEYSTORE -storepass:env PHONEFOCUS_SIGNING_PASSWORD -keypass:env PHONEFOCUS_SIGNING_PASSWORD -alias phonefocus -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Phone Focus, O=Local App, C=CN'
        if ($LASTEXITCODE -ne 0) { throw 'Signing key creation failed' }
    }
    $sdkProperty = $env:ANDROID_HOME.Replace('\','/').Replace(':','\:')
    [IO.File]::WriteAllText((Join-Path $PSScriptRoot 'local.properties'), "sdk.dir=$sdkProperty`n", [Text.UTF8Encoding]::new($false))
    $gradleArgs = @('-p', $PSScriptRoot, 'assembleRelease', ':app:lintRelease', '--no-daemon', '--console=plain')
    if (-not $Online) { $gradleArgs += '--offline' }
    & $gradleBin @gradleArgs
    if ($LASTEXITCODE -ne 0) { throw 'Android build or lint failed' }
    $releases = Join-Path $PSScriptRoot 'releases'
    New-Item -ItemType Directory -Path $releases -Force | Out-Null
    $apkName = "phone-focus-$taskVersion.apk"
    $apk = Join-Path $releases $apkName
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'app\build\outputs\apk\release\app-release.apk') -Destination $apk -Force
    & (Join-Path $env:ANDROID_HOME 'build-tools\36.0.0\apksigner.bat') verify --verbose $apk
    if ($LASTEXITCODE -ne 0) { throw 'APK signature check failed' }
    $hash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
    [IO.File]::WriteAllText(($apk + '.sha256'), "$hash  $apkName`n")
    Write-Output "APK ready: $apk"
} finally { Remove-Item Env:PHONEFOCUS_SIGNING_PASSWORD -ErrorAction SilentlyContinue }

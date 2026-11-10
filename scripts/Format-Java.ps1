param([switch]$Check)

$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$version = '1.29.0'
$expectedHash = 'AEB4E1831D56011E7E06F3393C4E17340C7CCA0FD7BA9076AB8DC0624759F7F0'
$cacheDirectory = Join-Path $env:LOCALAPPDATA 'AuxiRuta\formatters'
$formatter = Join-Path $cacheDirectory "google-java-format-$version-all-deps.jar"

if (!(Test-Path -LiteralPath $formatter)) {
    New-Item -ItemType Directory -Force $cacheDirectory | Out-Null
    $uri = "https://repo.maven.apache.org/maven2/com/google/googlejavaformat/google-java-format/$version/google-java-format-$version-all-deps.jar"
    Invoke-WebRequest -UseBasicParsing -Uri $uri -OutFile $formatter
}

if ((Get-FileHash -LiteralPath $formatter -Algorithm SHA256).Hash -ne $expectedHash) {
    throw 'El hash del formateador no coincide con la version fijada.'
}

$arguments = @(
    '-jar', $formatter,
    '--skip-sorting-imports',
    '--skip-removing-unused-imports',
    '--skip-reflowing-long-strings',
    '--skip-javadoc-formatting'
)

if ($Check) {
    $arguments += @('--dry-run', '--set-exit-if-changed')
} else {
    $arguments += '--replace'
}

$files = foreach ($module in @('auxiruta-commons', 'fleet-service', 'assistance-service', 'api-gateway')) {
    Get-ChildItem -LiteralPath (Join-Path $repo "$module/src") -Recurse -File -Filter '*.java'
}

& java @arguments @($files.FullName | Sort-Object)
if ($LASTEXITCODE -ne 0) {
    throw 'El formateador fallo o -Check detecto archivos pendientes de formato.'
}

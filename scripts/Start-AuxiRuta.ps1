param(
    [ValidateSet('docker', 'docker,demo')][string]$Profile = 'docker',
    [switch]$NoBuild
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$previousProfile = $env:SPRING_PROFILES_ACTIVE
Push-Location $repo
try {
    if (!(Test-Path -LiteralPath '.env')) { throw 'Copiar .env.example a .env y completar los secretos locales antes de iniciar.' }
    & docker info --format '{{.ServerVersion}}'
    if ($LASTEXITCODE -ne 0) { throw 'Iniciar Docker Desktop y esperar a que el motor este disponible.' }
    $env:SPRING_PROFILES_ACTIVE = $Profile
    & docker compose config --quiet
    if ($LASTEXITCODE -ne 0) { throw 'Configuracion Compose incompleta.' }
    if ($NoBuild) { & docker compose up -d } else { & docker compose up -d --build }
    if ($LASTEXITCODE -ne 0) { throw 'Fallo la construccion o el arranque.' }
    $deadline = (Get-Date).AddSeconds(120)
    do {
        $ready = $true
        foreach ($probe in @('http://fleet-service:8081/api/v1/operators/me', 'http://assistance-service:8082/api/v1/vehicles', 'http://localhost:8000/api/v1/vehicles')) {
            $result = & docker compose exec -T api-gateway curl -s -o /dev/null --max-time 3 -w '%{http_code}' $probe 2>$null
            if ($LASTEXITCODE -ne 0 -or $result -ne '401') { $ready = $false }
        }
        if (!$ready) { Start-Sleep -Seconds 2 }
    } while (!$ready -and (Get-Date) -lt $deadline)
    if (!$ready) { throw 'Servicios no disponibles en 120 s. Revisar docker compose logs --tail 80.' }
    Write-Host "AuxiRuta disponible en http://localhost:8000; Keycloak en http://localhost:8080. Perfil: $Profile"
} finally {
    $env:SPRING_PROFILES_ACTIVE = $previousProfile
    Pop-Location
}

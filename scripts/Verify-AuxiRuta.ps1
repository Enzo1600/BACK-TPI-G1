param([switch]$Integration, [switch]$IncludeDemo)
$ErrorActionPreference = 'Stop'
if ($IncludeDemo -and !$Integration) { throw 'IncludeDemo requiere Integration.' }
$repo = Split-Path $PSScriptRoot -Parent
function Invoke-Maven([string]$Module, [string[]]$Arguments) {
    & ".\$Module\mvnw.cmd" -f "$Module/pom.xml" @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Fallo Maven en $Module. Revisar target/surefire-reports." }
}
Push-Location $repo
try {
    Invoke-Maven 'auxiruta-commons' @('install')
    foreach ($module in @('fleet-service', 'assistance-service', 'api-gateway')) { Invoke-Maven $module @('verify') }
    if ($Integration) {
        & "$PSScriptRoot\Start-AuxiRuta.ps1" -Profile docker
        Invoke-Maven 'api-gateway' @('test', '-Dauxiruta.integration=true', '-Dauxiruta.realRouting=true', '-Dtest=BackendIntegrationTest')
        Invoke-Maven 'api-gateway' @('test', '-Dauxiruta.phase7=true', '-Dtest=Phase7IntegrationTest')
        Invoke-Maven 'api-gateway' @('test', '-Dauxiruta.phase8=true', '-Dtest=Phase8IntegrationTest')
        Invoke-Maven 'api-gateway' @('test', '-Dauxiruta.delivery=true', '-Dtest=DeliveryIntegrationTest')
        if ($IncludeDemo) {
            try {
                & "$PSScriptRoot\Start-AuxiRuta.ps1" -Profile 'docker,demo' -NoBuild
                Invoke-Maven 'api-gateway' @('test', '-Dauxiruta.phase9=true', '-Dtest=Phase9IntegrationTest')
            } finally { & "$PSScriptRoot\Start-AuxiRuta.ps1" -Profile docker -NoBuild }
        }
    }
    Write-Host 'Verificacion completada. Resultados JUnit en target/surefire-reports de cada modulo.'
} finally { Pop-Location }

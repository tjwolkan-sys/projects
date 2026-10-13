param(
    [switch]$BuildOnly,
    [switch]$StopOnly,
    [switch]$NoBuild,
    [switch]$NoDeploy
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

$ScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = (Resolve-Path (Join-Path $ScriptRoot "..\..")).Path
$AppRoot = Join-Path $RepoRoot "gbpowerdialer"
$ToolPathsFile = Join-Path $RepoRoot ".codex\TOOL-PATHS.local.properties"
$DefaultTomcatBase = Join-Path $RepoRoot ".vscode\.local\tomcat-base"

function Read-ToolPaths {
    param([string]$Path)

    $config = @{}
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        return $config
    }

    foreach ($line in Get-Content -LiteralPath $Path) {
        $trimmed = $line.Trim()
        if ([string]::IsNullOrWhiteSpace($trimmed) -or $trimmed.StartsWith("#")) {
            continue
        }

        $separatorIndex = $line.IndexOf("=")
        if ($separatorIndex -le 0) {
            continue
        }

        $key = $line.Substring(0, $separatorIndex).Trim()
        $value = $line.Substring($separatorIndex + 1).Trim()
        if (-not [string]::IsNullOrWhiteSpace($key)) {
            $config[$key] = $value
        }
    }

    return $config
}

function Get-ConfigValue {
    param(
        [hashtable]$Config,
        [string[]]$Keys,
        [string]$DefaultValue = $null
    )

    foreach ($key in $Keys) {
        if ($Config.ContainsKey($key) -and -not [string]::IsNullOrWhiteSpace($Config[$key])) {
            return $Config[$key]
        }
    }

    return $DefaultValue
}

function Resolve-CommandPath {
    param(
        [string]$Value,
        [string]$Label
    )

    if ([string]::IsNullOrWhiteSpace($Value)) {
        throw "$Label is not configured."
    }

    if ($Value.Contains("/") -or $Value.Contains("\") -or [System.IO.Path]::IsPathRooted($Value)) {
        $fullPath = [System.IO.Path]::GetFullPath($Value)
        if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
            throw "$Label not found: $fullPath"
        }
        return $fullPath
    }

    return $Value
}

function Resolve-OptionalFile {
    param(
        [string]$Value,
        [string]$Label
    )

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $null
    }

    $fullPath = [System.IO.Path]::GetFullPath($Value)
    if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
        throw "$Label not found: $fullPath"
    }
    return $fullPath
}

function Resolve-OptionalDirectory {
    param(
        [string]$Value,
        [string]$Label
    )

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $null
    }

    $fullPath = [System.IO.Path]::GetFullPath($Value)
    if (-not (Test-Path -LiteralPath $fullPath -PathType Container)) {
        throw "$Label not found: $fullPath"
    }
    return $fullPath
}

function Assert-ChildPath {
    param(
        [string]$Parent,
        [string]$Child
    )

    $parentFullPath = [System.IO.Path]::GetFullPath($Parent).TrimEnd([char[]]@("\", "/"))
    $childFullPath = [System.IO.Path]::GetFullPath($Child)
    $expectedPrefix = $parentFullPath + [System.IO.Path]::DirectorySeparatorChar

    if (-not $childFullPath.StartsWith($expectedPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Safety check failed: $childFullPath is not under $parentFullPath."
    }
}

function Ensure-Directory {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Container)) {
        New-Item -ItemType Directory -Path $Path -Force | Out-Null
    }
}

function Test-PortListening {
    param([int]$Port)

    if ($Port -le 0) {
        return $false
    }

    try {
        $connections = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
        return ($null -ne ($connections | Select-Object -First 1))
    }
    catch {
        $pattern = "^\s*TCP\s+(?:\[[^\]]+\]|[^\s:]+):$Port\s+\S+\s+LISTENING\s+\d+"
        $matches = netstat -ano -p tcp | Select-String -Pattern $pattern
        return ($null -ne ($matches | Select-Object -First 1))
    }
}

function Assert-PortFree {
    param(
        [int]$Port,
        [string]$Label,
        [string]$OverrideKey
    )

    if ($Port -le 0) {
        return
    }

    if (Test-PortListening $Port) {
        throw "$Label port $Port is already in use. Stop the owning process or set $OverrideKey in .codex/TOOL-PATHS.local.properties."
    }
}

function Wait-Port {
    param(
        [int]$Port,
        [string]$Label,
        [int]$TimeoutSeconds
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $client = New-Object System.Net.Sockets.TcpClient
        try {
            $asyncResult = $client.BeginConnect("127.0.0.1", $Port, $null, $null)
            if ($asyncResult.AsyncWaitHandle.WaitOne(1000)) {
                $client.EndConnect($asyncResult)
                Write-Host "$Label port ready: $Port"
                return
            }
        }
        catch {
            Start-Sleep -Milliseconds 500
        }
        finally {
            $client.Close()
        }
    }

    throw "$Label port did not open: $Port"
}

function Convert-ConfigBoolean {
    param(
        [string]$Value,
        [bool]$DefaultValue
    )

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $DefaultValue
    }

    $normalized = $Value.Trim().ToLowerInvariant()
    return @("1", "true", "yes", "y", "on").Contains($normalized)
}

function Test-TomcatProcessRunning {
    try {
        $processes = Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'" -ErrorAction Stop |
            Where-Object {
                $_.CommandLine -and
                $_.CommandLine.IndexOf("org.apache.catalina.startup.Bootstrap", [System.StringComparison]::OrdinalIgnoreCase) -ge 0 -and
                $_.CommandLine.IndexOf($TomcatBase, [System.StringComparison]::OrdinalIgnoreCase) -ge 0
            }

        return ($null -ne ($processes | Select-Object -First 1))
    }
    catch {
        return (Test-PortListening $TomcatShutdownPort)
    }
}

function Initialize-TomcatBase {
    $sourceConf = Join-Path $TomcatHome "conf"
    $targetConf = Join-Path $TomcatBase "conf"

    foreach ($directory in @($TomcatBase, $targetConf, (Join-Path $TomcatBase "logs"), (Join-Path $TomcatBase "temp"), (Join-Path $TomcatBase "webapps"), (Join-Path $TomcatBase "work"))) {
        Ensure-Directory $directory
    }

    if (-not (Test-Path -LiteralPath $sourceConf -PathType Container)) {
        throw "Tomcat conf directory not found: $sourceConf"
    }

    Get-ChildItem -LiteralPath $sourceConf | ForEach-Object {
        Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $targetConf $_.Name) -Recurse -Force
    }

    $serverXml = Join-Path $targetConf "server.xml"
    if (-not (Test-Path -LiteralPath $serverXml -PathType Leaf)) {
        throw "Generated Tomcat server.xml not found: $serverXml"
    }

    $content = Get-Content -LiteralPath $serverXml -Raw
    $content = [regex]::Replace($content, '<Server\s+port="[^"]+"', "<Server port=`"$TomcatShutdownPort`"")
    $httpConnectorRegex = [regex]'(<Connector\s+port=")\d+("\s+protocol="HTTP/1\.1")'
    $content = $httpConnectorRegex.Replace($content, "`${1}$TomcatHttpPort`${2}", 1)
    Set-Content -LiteralPath $serverXml -Value $content -Encoding UTF8
}

function Clear-ProtobufTempDirectory {
    $protobufTempDir = Join-Path $AppRoot "target\protoc-dependencies"
    if (-not (Test-Path -LiteralPath $protobufTempDir)) {
        return
    }

    Assert-ChildPath $AppRoot $protobufTempDir

    for ($attempt = 1; $attempt -le 5; $attempt++) {
        try {
            Remove-Item -LiteralPath $protobufTempDir -Recurse -Force -ErrorAction Stop
            Write-Host "Cleared protobuf temp directory: $protobufTempDir"
            return
        }
        catch {
            if ($attempt -eq 5) {
                throw "Could not clear protobuf temp directory: $protobufTempDir. Stop Tomcat and close tools that may index target, then retry. Last error: $($_.Exception.Message)"
            }

            Start-Sleep -Milliseconds 750
        }
    }
}

function Invoke-MavenPackage {
    $mavenArgs = @()

    if ($MavenOffline) {
        $mavenArgs += "-o"
    }

    if ($MavenSettings) {
        $mavenArgs += @("-s", $MavenSettings)
    }

    if ($MavenRepoLocal) {
        $mavenArgs += "-Dmaven.repo.local=$MavenRepoLocal"
    }

    if ($MavenProfile) {
        $mavenArgs += "-P$MavenProfile"
    }

    $mavenArgs += @("-Dmaven.test.skip=true", "clean", "package")

    Clear-ProtobufTempDirectory
    $mavenMode = if ($MavenOffline) { "offline" } else { "online" }
    Write-Host "Maven $mavenMode clean package starting. Profile: $MavenProfile"
    Push-Location $AppRoot
    try {
        & $MavenCmd @mavenArgs
        if ($LASTEXITCODE -ne 0) {
            throw "Maven package failed. Exit code: $LASTEXITCODE"
        }
    }
    finally {
        Pop-Location
    }
}

function Stop-Tomcat {
    if (-not (Test-TomcatProcessRunning)) {
        Write-Host "Tomcat is not running for base: $TomcatBase"
        return
    }

    Write-Host "Stopping Tomcat on shutdown port $TomcatShutdownPort."
    & $CatalinaBat stop
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "Tomcat stop returned non-zero exit code: $LASTEXITCODE"
    }
    Start-Sleep -Seconds $TomcatStopWaitSeconds
}

function Publish-War {
    $warSource = Join-Path $AppRoot "target\gbpowerdialer.war"
    if (-not (Test-Path -LiteralPath $warSource -PathType Leaf)) {
        throw "WAR not found: $warSource"
    }

    $webappsDir = Join-Path $TomcatBase "webapps"
    if (-not (Test-Path -LiteralPath $webappsDir -PathType Container)) {
        throw "Tomcat webapps directory not found: $webappsDir"
    }

    $warTarget = Join-Path $webappsDir "$ContextName.war"
    $explodedTarget = Join-Path $webappsDir $ContextName
    Assert-ChildPath $webappsDir $warTarget
    Assert-ChildPath $webappsDir $explodedTarget

    if (Test-Path -LiteralPath $explodedTarget) {
        Remove-Item -LiteralPath $explodedTarget -Recurse -Force
    }

    if (Test-Path -LiteralPath $warTarget) {
        Remove-Item -LiteralPath $warTarget -Force
    }

    Copy-Item -LiteralPath $warSource -Destination $warTarget -Force
    Write-Host "WAR deploy edildi: $warTarget"
}

function Wait-DebugPort {
    Wait-Port -Port $DebugPort -Label "Debug" -TimeoutSeconds $DebugPortTimeoutSeconds
}

function Start-TomcatDebug {
    Assert-PortFree -Port $DebugPort -Label "Debug" -OverrideKey "TOMCAT_DEBUG_PORT"
    Assert-PortFree -Port $TomcatHttpPort -Label "HTTP" -OverrideKey "TOMCAT_HTTP_PORT"
    Assert-PortFree -Port $TomcatShutdownPort -Label "Shutdown" -OverrideKey "TOMCAT_SHUTDOWN_PORT"

    $debugOptions = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=$DebugSuspend,address=$DebugPort"
    $existingCatalinaOpts = [Environment]::GetEnvironmentVariable("CATALINA_OPTS", "Process")

    if ([string]::IsNullOrWhiteSpace($existingCatalinaOpts)) {
        $env:CATALINA_OPTS = $debugOptions
    }
    elseif ($existingCatalinaOpts -notmatch "jdwp") {
        $env:CATALINA_OPTS = "$existingCatalinaOpts $debugOptions"
    }
    else {
        $env:CATALINA_OPTS = $existingCatalinaOpts
    }

    Write-Host "Tomcat starting in debug mode. Debug port: $DebugPort"
    & $CatalinaBat start
    if ($LASTEXITCODE -ne 0) {
        throw "Tomcat start failed. Exit code: $LASTEXITCODE"
    }

    Wait-DebugPort
    Wait-Port -Port $TomcatHttpPort -Label "HTTP" -TimeoutSeconds $TomcatHttpPortTimeoutSeconds

    Start-Sleep -Seconds $TomcatStabilityWaitSeconds
    if (-not (Test-PortListening $TomcatHttpPort)) {
        throw "Tomcat HTTP port closed after startup. Check Tomcat logs under $TomcatBase\logs."
    }

    if ($OpenBrowser) {
        $browserCommand = "Start-Sleep -Seconds $OpenBrowserDelaySeconds; Start-Process '$AppUrl'"
        Start-Process powershell -WindowStyle Hidden -ArgumentList @("-NoProfile", "-Command", $browserCommand)
        Write-Host "Browser will open in $OpenBrowserDelaySeconds seconds: $AppUrl"
    }
}

function Initialize-Tomcat {
    $script:TomcatHome = Resolve-OptionalDirectory (Get-ConfigValue $ToolPaths @("CATALINA_HOME", "TOMCAT_HOME") $null) "CATALINA_HOME/TOMCAT_HOME"
    if (-not $script:TomcatHome) {
        throw "CATALINA_HOME or TOMCAT_HOME must be configured in .codex/TOOL-PATHS.local.properties."
    }

    $tomcatBaseValue = Get-ConfigValue $ToolPaths @("TOMCAT_BASE") $DefaultTomcatBase
    $script:TomcatBase = [System.IO.Path]::GetFullPath($tomcatBaseValue)

    $script:TomcatShutdownPort = [int](Get-ConfigValue $ToolPaths @("TOMCAT_SHUTDOWN_PORT") "18005")
    $script:TomcatHttpPort = [int](Get-ConfigValue $ToolPaths @("TOMCAT_HTTP_PORT") "18080")
    $script:TomcatHttpPortTimeoutSeconds = [int](Get-ConfigValue $ToolPaths @("TOMCAT_HTTP_PORT_TIMEOUT_SECONDS") "180")
    $script:TomcatStabilityWaitSeconds = [int](Get-ConfigValue $ToolPaths @("TOMCAT_STABILITY_WAIT_SECONDS") "3")
    $script:OpenBrowser = Convert-ConfigBoolean (Get-ConfigValue $ToolPaths @("TOMCAT_OPEN_BROWSER") "true") $true
    $script:OpenBrowserDelaySeconds = [int](Get-ConfigValue $ToolPaths @("TOMCAT_OPEN_BROWSER_DELAY_SECONDS") "5")

    $env:CATALINA_HOME = $script:TomcatHome
    $env:CATALINA_BASE = $script:TomcatBase
    $script:CatalinaBat = Resolve-CommandPath (Join-Path $script:TomcatHome "bin\catalina.bat") "catalina.bat"

    $script:DebugPort = [int](Get-ConfigValue $ToolPaths @("TOMCAT_DEBUG_PORT") "5005")
    $script:DebugSuspend = Get-ConfigValue $ToolPaths @("TOMCAT_DEBUG_SUSPEND") "n"
    $script:DebugPortTimeoutSeconds = [int](Get-ConfigValue $ToolPaths @("TOMCAT_DEBUG_PORT_TIMEOUT_SECONDS") "60")
    $script:TomcatStopWaitSeconds = [int](Get-ConfigValue $ToolPaths @("TOMCAT_STOP_WAIT_SECONDS") "5")
    $script:ContextName = (Get-ConfigValue $ToolPaths @("APP_CONTEXT_PATH", "TOMCAT_CONTEXT_PATH") "gbpowerdialer").Trim().TrimStart("/").TrimEnd("/")

    if ([string]::IsNullOrWhiteSpace($script:ContextName)) {
        throw "APP_CONTEXT_PATH cannot be empty."
    }

    $script:AppUrl = "http://localhost:$TomcatHttpPort/$ContextName/"
    Initialize-TomcatBase
}

$ToolPaths = Read-ToolPaths $ToolPathsFile

$JavaHome = Resolve-OptionalDirectory (Get-ConfigValue $ToolPaths @("JAVA_HOME") $null) "JAVA_HOME"
if ($JavaHome) {
    $env:JAVA_HOME = $JavaHome
    $env:Path = (Join-Path $JavaHome "bin") + [System.IO.Path]::PathSeparator + $env:Path
}

if ($StopOnly) {
    Initialize-Tomcat
    Stop-Tomcat
    return
}

$MavenCmd = Resolve-CommandPath (Get-ConfigValue $ToolPaths @("MAVEN_CMD") "mvn.cmd") "MAVEN_CMD"
$MavenSettings = Resolve-OptionalFile (Get-ConfigValue $ToolPaths @("MAVEN_SETTINGS") $null) "MAVEN_SETTINGS"
$MavenRepoLocal = Resolve-OptionalDirectory (Get-ConfigValue $ToolPaths @("MAVEN_REPO_LOCAL") $null) "MAVEN_REPO_LOCAL"
$MavenProfile = Get-ConfigValue $ToolPaths @("MAVEN_PROFILE") "properties-tr-dev"
$MavenOffline = Convert-ConfigBoolean (Get-ConfigValue $ToolPaths @("VSCODE_MAVEN_OFFLINE") "false") $false

if (-not $BuildOnly) {
    Initialize-Tomcat
    Stop-Tomcat
}

if (-not $NoBuild) {
    Invoke-MavenPackage
}

if ($BuildOnly) {
    return
}

if (-not $NoDeploy) {
    Publish-War
}

Start-TomcatDebug

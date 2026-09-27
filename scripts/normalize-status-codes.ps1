param(
    [ValidateSet('check', 'apply', 'verify', 'rollback')]
    [string]$Action = 'check'
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent

# 使用项目临时目录，避免系统临时路径影响 Java 运行。
New-Item -ItemType Directory -Force (Join-Path $repo '.runtime/tmp') | Out-Null
$env:TEMP = Join-Path $repo '.runtime/tmp'
$env:TMP = $env:TEMP

# 使用自定义 Maven 本地仓库时，通过 MAVEN_REPO 覆盖默认位置。
$mavenRepo = if ($env:MAVEN_REPO) {
    $env:MAVEN_REPO
} else {
    (mvn help:evaluate '-Dexpression=settings.localRepository' -q -DforceStdout | Out-String).Trim()
}

$jars = @(
    'com/mysql/mysql-connector-j/9.7.0/mysql-connector-j-9.7.0.jar',
    'com/fasterxml/jackson/core/jackson-annotations/2.17.2/jackson-annotations-2.17.2.jar',
    'com/fasterxml/jackson/core/jackson-core/2.17.2/jackson-core-2.17.2.jar',
    'com/fasterxml/jackson/core/jackson-databind/2.17.2/jackson-databind-2.17.2.jar'
) | ForEach-Object { Join-Path $mavenRepo $_ }

foreach ($jar in $jars) {
    if (!(Test-Path -LiteralPath $jar)) {
        throw "Required dependency missing: $jar"
    }
}

java '-Dfile.encoding=UTF-8' -cp ($jars -join [IO.Path]::PathSeparator) (Join-Path $PSScriptRoot 'NormalizeStatusCodes.java') $Action
if ($LASTEXITCODE -ne 0) {
    throw "Migration $Action failed; see the error above."
}

param([ValidateSet('check','apk','unsigned')][string]$Target='check')
$ErrorActionPreference='Stop'
Set-Location -LiteralPath $PSScriptRoot
$sharedTools=Join-Path $PSScriptRoot '../../.tools/android-build'
if(-not $env:JAVA_HOME -and (Test-Path -LiteralPath "$sharedTools/jdk17-current")){$env:JAVA_HOME=(Resolve-Path -LiteralPath "$sharedTools/jdk17-current").Path}
if(-not $env:GRADLE_USER_HOME -and (Test-Path -LiteralPath "$sharedTools/gradle-home")){$env:GRADLE_USER_HOME=(Resolve-Path -LiteralPath "$sharedTools/gradle-home").Path}
$jvmTemp=Join-Path $PSScriptRoot '.local/jvm-tmp'
New-Item -ItemType Directory -Force -Path $jvmTemp | Out-Null
$savedOptions=$env:JAVA_TOOL_OPTIONS
$env:JAVA_TOOL_OPTIONS="$savedOptions -Djava.io.tmpdir=$jvmTemp -Djdk.net.unixdomain.tmpdir=$jvmTemp"
try {
    $arguments=@(switch($Target){'check'{@('test')};'apk'{@('test',':app:assembleRelease')};'unsigned'{@('test',':app:assembleRelease','-PunsignedBuild=true')}})
    & "$PSScriptRoot/gradlew.bat" @arguments --console=plain
    $result=$LASTEXITCODE
} finally {$env:JAVA_TOOL_OPTIONS=$savedOptions}
exit $result

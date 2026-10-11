<# 无 Maven 的隔离集成测试入口；安装路径仅用于读取程序，产物写入 out/。 #>
param(
    [Parameter(Mandatory = $true)][string]$TomcatHome,
    [Parameter(Mandatory = $true)][string]$MysqlHome
)
$ErrorActionPreference = 'Stop'
$deliveryRoot = Split-Path -Parent $PSScriptRoot
$deliveryTomcatHome = (Resolve-Path -LiteralPath $TomcatHome).Path
$deliveryMysqlHome = (Resolve-Path -LiteralPath $MysqlHome).Path
$deliveryMysql = Join-Path $deliveryMysqlHome 'bin/mysql.exe'
$deliveryMysqld = Join-Path $deliveryMysqlHome 'bin/mysqld.exe'
$deliveryMysqlAdmin = Join-Path $deliveryMysqlHome 'bin/mysqladmin.exe'
$deliveryServletApi = Join-Path $deliveryTomcatHome 'lib/servlet-api.jar'
foreach ($deliveryRequired in @($deliveryMysql, $deliveryMysqld, $deliveryMysqlAdmin, $deliveryServletApi)) {
    if (!(Test-Path -LiteralPath $deliveryRequired)) { throw "缺少依赖：$deliveryRequired" }
}
$deliveryJava = (Get-Command java -ErrorAction Stop).Source
$deliveryJavac = (Get-Command javac -ErrorAction Stop).Source
$deliveryNode = (Get-Command node -ErrorAction Stop).Source
foreach ($deliveryPort in @(13306, 18080)) {
    if (Get-NetTCPConnection -State Listen -LocalPort $deliveryPort -ErrorAction SilentlyContinue) {
        throw "隔离测试端口 $deliveryPort 已被占用，请先关闭此前的测试实例。"
    }
}
$deliveryRun = Join-Path $deliveryRoot ('out/reliability-run-' + [Guid]::NewGuid().ToString('N'))
$deliveryBase = Join-Path $deliveryRun 'tomcat'
$deliveryData = Join-Path $deliveryRun 'mysql-data'
$deliveryClasses = Join-Path $deliveryBase 'webapps/DeliveryWeb/WEB-INF/classes'
$deliveryMysqlProcess = $null
$deliveryTomcatProcess = $null
$deliveryOldMysqlBin = $env:MYSQL_TEST_BIN

function Invoke-DeliveryChecked([string]$Executable, [string[]]$Arguments) {
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) { throw "命令失败（退出码 $LASTEXITCODE）：$Executable" }
}
function Wait-DeliveryPort([int]$Port, $Process) {
    $deliveryDeadline = [DateTime]::UtcNow.AddSeconds(30)
    while ([DateTime]::UtcNow -lt $deliveryDeadline) {
        if ($Process.HasExited) { throw "测试进程提前退出，请查看 $deliveryRun 下的日志。" }
        $deliverySocket = [System.Net.Sockets.TcpClient]::new()
        try { $deliverySocket.Connect('127.0.0.1', $Port); return }
        catch { Start-Sleep -Milliseconds 300 }
        finally { $deliverySocket.Dispose() }
    }
    throw "测试端口 $Port 启动超时，请查看 $deliveryRun 下的日志。"
}

try {
    New-Item -ItemType Directory -Force -Path $deliveryData,
        "$deliveryBase/conf", "$deliveryBase/logs", "$deliveryBase/temp", "$deliveryBase/work", "$deliveryBase/webapps" | Out-Null
    Copy-Item -Path (Join-Path $deliveryTomcatHome 'conf/*') -Destination "$deliveryBase/conf"
    Copy-Item -LiteralPath (Join-Path $deliveryRoot 'webapp') -Destination "$deliveryBase/webapps/DeliveryWeb" -Recurse
    New-Item -ItemType Directory -Force -Path $deliveryClasses | Out-Null
    $deliverySources = @(Get-ChildItem -LiteralPath "$deliveryRoot/src/com" -Recurse -Filter '*.java' | ForEach-Object FullName)
    Invoke-DeliveryChecked $deliveryJavac (@('--release', '8', '-encoding', 'UTF-8', '-cp',
        "$deliveryRoot/webapp/WEB-INF/lib/*;$deliveryServletApi", '-d', $deliveryClasses) + $deliverySources)
    $deliveryConfig = (Get-Content -LiteralPath "$deliveryRoot/src/resources/mybatis-config.xml.sample" -Raw -Encoding UTF8).Replace(
        'localhost:3306/DeliveryWeb_db', '127.0.0.1:13306/DeliveryWeb_reliability').Replace('CHANGE_ME', '')
    Set-Content -LiteralPath "$deliveryClasses/mybatis-config.xml" -Value $deliveryConfig -Encoding UTF8
    Set-Content -LiteralPath "$deliveryBase/conf/server.xml" -Encoding UTF8 -Value @'
<Server port="-1"><Service name="Catalina"><Connector address="127.0.0.1" port="18080" protocol="HTTP/1.1" connectionTimeout="20000"/><Engine name="Catalina" defaultHost="localhost"><Host name="localhost" appBase="webapps" unpackWARs="true" autoDeploy="false"/></Engine></Service></Server>
'@
    $deliverySql = (Get-Content -LiteralPath "$deliveryRoot/sql/init.sql" -Raw -Encoding UTF8).Replace(
        'DeliveryWeb_db', 'DeliveryWeb_reliability')
    Set-Content -LiteralPath "$deliveryRun/init.sql" -Value $deliverySql -Encoding UTF8
    Invoke-DeliveryChecked $deliveryMysqld @('--no-defaults', '--initialize-insecure',
        "--basedir=$deliveryMysqlHome", "--datadir=$deliveryData", '--console')
    $deliveryMysqlProcess = Start-Process -FilePath $deliveryMysqld -ArgumentList @('--no-defaults',
        "--basedir=`"$deliveryMysqlHome`"", "--datadir=`"$deliveryData`"", '--port=13306',
        '--bind-address=127.0.0.1', '--mysqlx=0', '--console') -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput "$deliveryRun/mysql.stdout.log" -RedirectStandardError "$deliveryRun/mysql.stderr.log"
    Wait-DeliveryPort 13306 $deliveryMysqlProcess
    $deliverySqlPath = "$deliveryRun/init.sql".Replace('\', '/')
    Invoke-DeliveryChecked $deliveryMysql @('--no-defaults', '--host=127.0.0.1', '--port=13306',
        '--user=root', '--default-character-set=utf8mb4', "--execute=source $deliverySqlPath")
    $deliveryBootstrap = "$deliveryTomcatHome/bin/bootstrap.jar;$deliveryTomcatHome/bin/tomcat-juli.jar"
    $deliveryTomcatProcess = Start-Process -FilePath $deliveryJava -ArgumentList @(
        "`"-Dcatalina.home=$deliveryTomcatHome`"", "`"-Dcatalina.base=$deliveryBase`"",
        "`"-Djava.io.tmpdir=$deliveryBase/temp`"", '-cp', "`"$deliveryBootstrap`"",
        'org.apache.catalina.startup.Bootstrap', 'start') -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput "$deliveryRun/tomcat.stdout.log" -RedirectStandardError "$deliveryRun/tomcat.stderr.log"
    Wait-DeliveryPort 18080 $deliveryTomcatProcess
    $env:MYSQL_TEST_BIN = $deliveryMysql
    Invoke-DeliveryChecked $deliveryNode @("$PSScriptRoot/dw-syntax-check.mjs")
    Invoke-DeliveryChecked $deliveryNode @("$PSScriptRoot/check-unused-css.mjs")
    foreach ($deliverySuite in @('auth-test.mjs', 'session-test.mjs', 'smoke-test.mjs', 'reliability-test.mjs')) {
        Invoke-DeliveryChecked $deliveryNode @("$PSScriptRoot/$deliverySuite", 'http://127.0.0.1:18080/DeliveryWeb')
    }
    Invoke-DeliveryChecked $deliveryNode @("$PSScriptRoot/smoke-test.mjs", 'http://127.0.0.1:18080/DeliveryWeb', '--with-pay')
    Write-Host "全部隔离回归测试通过。产物和日志：$deliveryRun"
} finally {
    $env:MYSQL_TEST_BIN = $deliveryOldMysqlBin
    if ($deliveryTomcatProcess -and !$deliveryTomcatProcess.HasExited) {
        Stop-Process -Id $deliveryTomcatProcess.Id -ErrorAction SilentlyContinue
    }
    if ($deliveryMysqlProcess -and !$deliveryMysqlProcess.HasExited) {
        & $deliveryMysqlAdmin --no-defaults --host=127.0.0.1 --port=13306 --user=root shutdown
        if (!$deliveryMysqlProcess.WaitForExit(10000)) { Stop-Process -Id $deliveryMysqlProcess.Id -ErrorAction SilentlyContinue }
    }
}

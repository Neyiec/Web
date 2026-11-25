param(
    [string]$MySqlHost = '127.0.0.1',
    [ValidateRange(1, 65535)]
    [int]$Port = 3306,
    [string]$User = 'root',
    [switch]$PromptForPassword
)

$ErrorActionPreference = 'Stop'
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$sqlDirectory = Join-Path (Split-Path -Parent $PSScriptRoot) 'sql'
$mysql = Get-Command mysql -CommandType Application

# Load the normal schema in a database reserved for the classroom demo.
$schema = Get-Content -LiteralPath (Join-Path $sqlDirectory '01_create_schema.sql') -Raw -Encoding utf8
$roles = Get-Content -LiteralPath (Join-Path $sqlDirectory '02_init_data.sql') -Raw -Encoding utf8
if (!$schema.Contains('CREATE DATABASE IF NOT EXISTS campus_market') -or
    !$schema.Contains('USE campus_market;') -or !$roles.Contains('USE campus_market;')) {
    throw '建表或初始化脚本的库名已变更，请先核对演示库映射。'
}
$schema = $schema.Replace('CREATE DATABASE IF NOT EXISTS campus_market', 'CREATE DATABASE IF NOT EXISTS campus_market_demo')
$schema = $schema.Replace('USE campus_market;', 'USE campus_market_demo;')
$roles = $roles.Replace('USE campus_market;', 'USE campus_market_demo;')
$demo = Get-Content -LiteralPath (Join-Path $sqlDirectory '03_demo_data.sql') -Raw -Encoding utf8

$clientArgs = @(
    '--no-defaults', '--protocol=TCP', '--default-character-set=utf8mb4', '--batch',
    "--host=$MySqlHost", "--port=$Port", "--user=$User"
)
if ($PromptForPassword) {
    $clientArgs += '--password'
}

($schema, $roles, $demo -join [Environment]::NewLine) | & $mysql.Source @clientArgs
if ($LASTEXITCODE -ne 0) {
    throw "演示数据初始化失败，MySQL 退出码为 $LASTEXITCODE。"
}
Write-Output '演示数据已导入 campus_market_demo。'

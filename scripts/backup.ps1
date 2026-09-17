<#
.SYNOPSIS
  SiteFlow Database Backup Script (PowerShell)
.DESCRIPTION
  Performs a consistent logical backup of the SiteFlow MySQL database using mysqldump.
  Configurable via environment variables or parameter arguments.
  Credentials are never committed or printed to logs.
#>

param(
    [string]$HostName = $(if ($env:DB_HOST) { $env:DB_HOST } else { "localhost" }),
    [int]$Port = $(if ($env:DB_PORT) { [int]$env:DB_PORT } else { 3306 }),
    [string]$Database = $(if ($env:DB_NAME) { $env:DB_NAME } else { "siteflow" }),
    [string]$User = $(if ($env:DB_USER) { $env:DB_USER } else { "root" }),
    [string]$Password = $env:DB_PASSWORD,
    [string]$BackupDir = $(if ($env:BACKUP_DIR) { $env:BACKUP_DIR } else { "backups" })
)

if (-not (Test-Path $BackupDir)) {
    New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
}

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$backupFile = Join-Path $BackupDir "siteflow_backup_${Database}_${timestamp}.sql"

Write-Host "[BACKUP] Starting backup for database '${Database}' on ${HostName}:${Port}..."

$dumpArgs = @(
    "--host=$HostName",
    "--port=$Port",
    "--user=$User",
    "--single-transaction",
    "--quick",
    "--routines",
    "--triggers",
    "--set-gtid-purged=OFF",
    "--result-file=$backupFile",
    $Database
)

if ($Password) {
    $env:MYSQL_PWD = $Password
}

try {
    & mysqldump.exe $dumpArgs
    if ($LASTEXITCODE -eq 0 -and (Test-Path $backupFile)) {
        $fileSize = (Get-Item $backupFile).Length
        Write-Host "[BACKUP SUCCESS] Backup created successfully: $backupFile ($fileSize bytes)"
        $backupFile
    } else {
        Write-Error "[BACKUP FAILED] mysqldump exited with code $LASTEXITCODE"
        exit 1
    }
} finally {
    if ($env:MYSQL_PWD) {
        Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
    }
}

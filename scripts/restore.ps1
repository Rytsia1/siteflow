<#
.SYNOPSIS
  SiteFlow Database Restore Script (PowerShell)
.DESCRIPTION
  Restores a SiteFlow MySQL database from a logical backup file using mysql.exe.
  Supports restoring into an isolated/clean test database (e.g. siteflow_recovery_test)
  without impacting the primary database.
  Configurable via environment variables or parameter arguments.
#>

param(
    [string]$BackupFile = "",
    [string]$HostName = $(if ($env:DB_HOST) { $env:DB_HOST } else { "localhost" }),
    [int]$Port = $(if ($env:DB_PORT) { [int]$env:DB_PORT } else { 3306 }),
    [string]$TargetDatabase = $(if ($env:DB_NAME) { $env:DB_NAME } else { "siteflow" }),
    [string]$User = $(if ($env:DB_USER) { $env:DB_USER } elseif ($env:DB_USERNAME) { $env:DB_USERNAME } else { "siteflow_admin" }),
    [string]$Password = $env:DB_PASSWORD,
    [string]$SslMode = $(if ($env:DB_SSL_MODE) { $env:DB_SSL_MODE } else { "PREFERRED" }),
    [string]$BackupDir = $(if ($env:BACKUP_DIR) { $env:BACKUP_DIR } else { "backups" }),
    [switch]$CleanDatabase,
    [switch]$Force
)

# Resolve backup file if not explicitly passed
if (-not $BackupFile) {
    if (Test-Path $BackupDir) {
        $latest = Get-ChildItem -Path $BackupDir -Filter "*.sql" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if ($latest) {
            $BackupFile = $latest.FullName
        }
    }
}

if (-not $BackupFile -or -not (Test-Path $BackupFile)) {
    Write-Error "[RESTORE FAILED] Backup file not found: '$BackupFile'"
    exit 1
}

Write-Host "[RESTORE] Target Database: '$TargetDatabase' on ${HostName}:${Port} (User: $User, SSL: $SslMode)"
Write-Host "[RESTORE] Source Backup  : '$BackupFile'"

if ($Password) {
    $env:MYSQL_PWD = $Password
}

try {
    # If CleanDatabase is specified, drop and recreate target database safely
    if ($CleanDatabase) {
        if ($TargetDatabase -eq "siteflow" -and -not $Force) {
            Write-Error "[RESTORE SAFETY] Cannot drop primary database 'siteflow' without -Force flag."
            exit 1
        }
        Write-Host "[RESTORE] Cleaning (dropping & recreating) target database '$TargetDatabase'..."
        $dropCreateSql = "DROP DATABASE IF EXISTS $TargetDatabase; CREATE DATABASE $TargetDatabase CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
        & mysql.exe --host=$HostName --port=$Port --user=$User --ssl-mode=$SslMode -e $dropCreateSql
        if ($LASTEXITCODE -ne 0) {
            Write-Error "[RESTORE FAILED] Could not create database '$TargetDatabase'"
            exit 1
        }
    } else {
        # Ensure database exists
        $createSql = "CREATE DATABASE IF NOT EXISTS $TargetDatabase CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
        & mysql.exe --host=$HostName --port=$Port --user=$User --ssl-mode=$SslMode -e $createSql
        if ($LASTEXITCODE -ne 0) {
            Write-Error "[RESTORE FAILED] Could not ensure database '$TargetDatabase' exists"
            exit 1
        }
    }

    Write-Host "[RESTORE] Importing backup SQL into '$TargetDatabase'..."
    # Format path with forward slashes for MySQL source command
    $normalizedPath = ($BackupFile -replace '\\', '/')
    $sourceSql = "source $normalizedPath;"
    & mysql.exe --host=$HostName --port=$Port --user=$User --ssl-mode=$SslMode --database=$TargetDatabase -e $sourceSql

    if ($LASTEXITCODE -eq 0) {
        Write-Host "[RESTORE SUCCESS] Database '$TargetDatabase' successfully restored from '$BackupFile'."
    } else {
        Write-Error "[RESTORE FAILED] mysql.exe exited with code $LASTEXITCODE during restore."
        exit 1
    }
} finally {
    if ($env:MYSQL_PWD) {
        Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
    }
}

# =============================================================================
# SiteFlow — Administrator Provisioning Script (PowerShell)
#
# Usage:
#   .\scripts\provision-admin.ps1
#
# Interactively prompts for username, display name, and securely reads the
# password (hidden input) to prevent exposure in process lists or shell history.
# =============================================================================

[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host " SiteFlow — Explicit Administrator Provisioning" -ForegroundColor Cyan
Write-Host "========================================================"
Write-Host ""

$username = Read-Host "Enter admin username"
if ([string]::IsNullOrWhiteSpace($username)) {
    Write-Error "Username cannot be empty."
    exit 1
}

$fullName = Read-Host "Enter full name [System Administrator]"
if ([string]::IsNullOrWhiteSpace($fullName)) {
    $fullName = "System Administrator"
}

$jobPosition = Read-Host "Enter job position [Site Administrator]"
if ([string]::IsNullOrWhiteSpace($jobPosition)) {
    $jobPosition = "Site Administrator"
}

$securePassword = Read-Host "Enter admin password (min 8 characters)" -AsSecureString
$bstr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
$password = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr)
[System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)

if ($password.Length -lt 8) {
    Write-Error "Password must be at least 8 characters long."
    exit 1
}

$secureConfirm = Read-Host "Confirm admin password" -AsSecureString
$bstrConfirm = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureConfirm)
$passwordConfirm = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($bstrConfirm)
[System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstrConfirm)

if ($password -ne $passwordConfirm) {
    Write-Error "Passwords do not match."
    exit 1
}

Write-Host ""
Write-Host "Provisioning administrator '$username'..." -ForegroundColor Green

$env:SITEFLOW_PROVISION_ADMIN = "true"
$env:SITEFLOW_ADMIN_USERNAME = $username
$env:SITEFLOW_ADMIN_PASSWORD = $password
$env:SITEFLOW_ADMIN_FULL_NAME = $fullName
$env:SITEFLOW_ADMIN_JOB_POSITION = $jobPosition
$env:SITEFLOW_PROVISION_ADMIN_EXIT_AFTER = "true"

try {
    if (Test-Path "target\siteflow-0.0.1-SNAPSHOT.jar") {
        java -jar target\siteflow-0.0.1-SNAPSHOT.jar
    } else {
        mvn spring-boot:run -Dspring-boot.run.arguments="--siteflow.provision-admin=true --siteflow.provision-admin.exit-after=true"
    }
} finally {
    Remove-Item Env:\SITEFLOW_ADMIN_PASSWORD -ErrorAction SilentlyContinue
}

Write-Host "Administrator '$username' provisioning finished successfully." -ForegroundColor Green

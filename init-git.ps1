#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Initializes the VSU monorepo as a git repository and creates the first commit.
    Run this ONCE after installing Git for Windows.
.NOTES
    Requires: Git for Windows (https://git-scm.com/download/win)
#>

$repoRoot = $PSScriptRoot

Write-Host "`n[VSU] Initializing git repository at $repoRoot`n" -ForegroundColor Cyan

# Init
git -C $repoRoot init
git -C $repoRoot checkout -b main

# Set identity if not configured (update these!)
$user = git -C $repoRoot config user.name 2>$null
if (-not $user) {
    Write-Host "[VSU] Git user not configured. Please set your identity:" -ForegroundColor Yellow
    $name  = Read-Host "  Git user.name"
    $email = Read-Host "  Git user.email"
    git -C $repoRoot config user.name  $name
    git -C $repoRoot config user.email $email
}

# Initial commit
git -C $repoRoot add .
git -C $repoRoot commit -m "feat: initial multi-version VSU monorepo

- 1.18.2 (Fabric + Forge)
- 1.19.2 (Fabric + Forge)
- 1.20.1 (Fabric + Forge)
- 1.21.1 (Fabric + NeoForge)
- Fix SQLite JDBC driver discovery on Forge/NeoForge (Class.forName)
- Unified build-menu.py with Java auto-detection
- Proper server-side-only configuration across all versions"

Write-Host "`n[VSU] Repository initialized with initial commit." -ForegroundColor Green
Write-Host "[VSU] To push to GitHub, run:" -ForegroundColor Cyan
Write-Host "  git remote add origin <your-github-url>"
Write-Host "  git push -u origin main"
Write-Host ""

# WSL 설치 및 구성 스크립트
# 관리자 권한으로 실행 필요

Write-Host "=== WSL 설치 및 구성 ===" -ForegroundColor Green

# WSL 설치 여부 확인
Write-Host "`n1. WSL 설치 상태 확인 중..." -ForegroundColor Yellow
$wslInstalled = Get-Command wsl -ErrorAction SilentlyContinue

if (-not $wslInstalled) {
    Write-Host "WSL이 설치되어 있지 않습니다." -ForegroundColor Red
    Write-Host "`nWSL 설치를 시작합니다..." -ForegroundColor Yellow
    
    # WSL 기능 활성화
    Write-Host "`n2. WSL 기능 활성화 중..." -ForegroundColor Yellow
    try {
        Enable-WindowsOptionalFeature -Online -FeatureName Microsoft-Windows-Subsystem-Linux -NoRestart -ErrorAction Stop
        Write-Host "WSL 기능이 활성화되었습니다." -ForegroundColor Green
    } catch {
        Write-Host "오류: WSL 기능 활성화 실패 - $_" -ForegroundColor Red
        Write-Host "관리자 권한으로 실행했는지 확인하세요." -ForegroundColor Yellow
        exit 1
    }
    
    # Virtual Machine Platform 활성화 (WSL2용)
    Write-Host "`n3. Virtual Machine Platform 활성화 중..." -ForegroundColor Yellow
    try {
        Enable-WindowsOptionalFeature -Online -FeatureName VirtualMachinePlatform -NoRestart -ErrorAction Stop
        Write-Host "Virtual Machine Platform이 활성화되었습니다." -ForegroundColor Green
    } catch {
        Write-Host "경고: Virtual Machine Platform 활성화 실패 - $_" -ForegroundColor Yellow
    }
    
    Write-Host "`n설치가 완료되었습니다. 시스템을 재시작한 후 다음 명령을 실행하세요:" -ForegroundColor Green
    Write-Host "  wsl --install" -ForegroundColor Cyan
    Write-Host "`n또는 특정 배포판을 설치하려면:" -ForegroundColor Yellow
    Write-Host "  wsl --install -d Ubuntu" -ForegroundColor Cyan
    Write-Host "  wsl --install -d Debian" -ForegroundColor Cyan
    
} else {
    Write-Host "WSL이 이미 설치되어 있습니다." -ForegroundColor Green
    
    # 설치된 배포판 확인
    Write-Host "`n2. 설치된 배포판 확인 중..." -ForegroundColor Yellow
    $distros = wsl --list --verbose 2>&1
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host $distros
    } else {
        Write-Host "배포판이 설치되어 있지 않습니다." -ForegroundColor Yellow
        Write-Host "`nUbuntu 설치를 권장합니다:" -ForegroundColor Yellow
        Write-Host "  wsl --install -d Ubuntu" -ForegroundColor Cyan
    }
}

Write-Host "`n=== 완료 ===" -ForegroundColor Green
Write-Host "`n다음 단계:" -ForegroundColor Yellow
Write-Host "1. 시스템 재시작 (필요시)" -ForegroundColor White
Write-Host "2. WSL 배포판 설치: wsl --install -d Ubuntu" -ForegroundColor White
Write-Host "3. WSL에서 프로젝트 디렉토리 접근" -ForegroundColor White
Write-Host "   예: cd /mnt/e/GoogleDrive/내\\ 드라이브/03.\\ Coding/Cursor/OLTPBench-for-multiple-dbms" -ForegroundColor Cyan



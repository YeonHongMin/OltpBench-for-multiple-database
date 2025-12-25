# WSL 구성 가이드

Windows에서 Linux 환경을 사용하여 `start_build_oracle.sh` 스크립트를 실행하기 위한 WSL(Windows Subsystem for Linux) 구성 가이드입니다.

## 1. WSL 설치

### 방법 1: PowerShell 스크립트 사용 (권장)

1. **PowerShell을 관리자 권한으로 실행**
   - Windows 키 + X → "Windows PowerShell (관리자)" 선택

2. **스크립트 실행**
   ```powershell
   cd "E:\GoogleDrive\내 드라이브\03. Coding\Cursor\OLTPBench-for-multiple-dbms"
   .\setup_wsl.ps1
   ```

3. **시스템 재시작** (필요시)

4. **Ubuntu 설치**
   ```powershell
   wsl --install -d Ubuntu
   ```

### 방법 2: 수동 설치

1. **PowerShell을 관리자 권한으로 실행**

2. **WSL 기능 활성화**
   ```powershell
   dism.exe /online /enable-feature /featurename:Microsoft-Windows-Subsystem-Linux /all /norestart
   dism.exe /online /enable-feature /featurename:VirtualMachinePlatform /all /norestart
   ```

3. **시스템 재시작**

4. **WSL 2로 업데이트** (선택사항)
   ```powershell
   wsl --set-default-version 2
   ```

5. **Ubuntu 설치**
   ```powershell
   wsl --install -d Ubuntu
   ```

## 2. WSL 초기 설정

1. **WSL 실행**
   ```powershell
   wsl
   ```
   또는 시작 메뉴에서 "Ubuntu" 검색

2. **사용자 계정 생성**
   - 첫 실행 시 사용자명과 비밀번호를 설정합니다.

3. **시스템 업데이트**
   ```bash
   sudo apt update
   sudo apt upgrade -y
   ```

## 3. 프로젝트 디렉토리 접근

Windows 파일 시스템은 WSL에서 `/mnt/` 경로로 접근할 수 있습니다.

### 프로젝트 디렉토리로 이동
```bash
cd /mnt/e/GoogleDrive/내\ 드라이브/03.\ Coding/Cursor/OLTPBench-for-multiple-dbms
```

### 또는 심볼릭 링크 생성 (선택사항)
```bash
# 홈 디렉토리에 프로젝트 링크 생성
ln -s /mnt/e/GoogleDrive/내\ 드라이브/03.\ Coding/Cursor/OLTPBench-for-multiple-dbms ~/oltpbench
cd ~/oltpbench
```

## 4. 필요한 도구 설치

### Java 설치 확인 및 설치
```bash
# Java 버전 확인
java -version

# Java가 없으면 설치 (OpenJDK 11 예시)
sudo apt install openjdk-11-jdk -y
```

### Maven 설치 (프로젝트 빌드용)
```bash
sudo apt install maven -y
```

### Git 설치 (이미 설치되어 있을 수 있음)
```bash
sudo apt install git -y
```

## 5. 스크립트 실행 권한 부여

```bash
cd /mnt/e/GoogleDrive/내\ 드라이브/03.\ Coding/Cursor/OLTPBench-for-multiple-dbms

# 실행 권한 부여
chmod +x start_build_oracle.sh
chmod +x oltpbenchmark
chmod +x classpath.sh
```

## 6. 프로젝트 빌드

```bash
# Maven을 사용하여 프로젝트 빌드
mvn clean compile

# 또는 전체 빌드
mvn clean package
```

## 7. 스크립트 실행

```bash
# Oracle 빌드 스크립트 실행
./start_build_oracle.sh
```

## 문제 해결

### WSL이 설치되지 않는 경우
- Windows 10 버전 2004 이상 또는 Windows 11이 필요합니다.
- BIOS에서 가상화 기능이 활성화되어 있는지 확인하세요.

### 파일 경로 문제
- Windows 경로의 공백은 백슬래시(`\`)로 이스케이프해야 합니다.
- 예: `내\ 드라이브`, `03.\ Coding`

### 권한 문제
- 스크립트에 실행 권한이 없으면 `chmod +x` 명령으로 부여하세요.

### Java 경로 문제
- `JAVA_HOME` 환경 변수를 설정해야 할 수 있습니다.
  ```bash
  export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
  export PATH=$JAVA_HOME/bin:$PATH
  ```

## 유용한 WSL 명령어

```bash
# WSL 종료
exit

# Windows에서 WSL 종료
wsl --shutdown

# 설치된 배포판 목록
wsl --list --verbose

# 기본 배포판 설정
wsl --set-default Ubuntu

# WSL 버전 확인
wsl --status
```

## 참고 자료

- [Microsoft WSL 공식 문서](https://docs.microsoft.com/windows/wsl/)
- [WSL 설치 가이드](https://aka.ms/wslinstall)



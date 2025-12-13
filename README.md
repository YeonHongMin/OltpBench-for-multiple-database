# OLTPBench for Multiple DBMS
기존 OltpBench가 BenchBase으로 옮겨졌으나 기존 OltpBench의 기능을 유지하기 위해 이 프로젝트를 진행합니다. 특히, Multi-Database에 대한 지원을 추가하기 위해 이 프로젝트를 진행합니다.
Connection Polling 기능을 추가하여 어떤 에러가 발생해도 재접속 하도록 변경하였습니다.
- 2025.12.13

## 지원되는 DBMS
- MySQL
- PostgreSQL
- Oracle
- DB2
- MSSQL (SQL Server)
- Tibero

## 사전 요구 사항 (Prerequisites)
- Java (+1.7, JDK 11+ 권장)
- Maven (Dependency 관리를 위해 변환됨)
- 각 DBMS에 대한 JDBC 드라이버 (Maven dependency 또는 lib 폴더에 포함됨)

## 프로젝트 빌드
소스 코드가 수정되었거나 처음 실행하는 경우 Maven을 통해 빌드해야 합니다.
```bash
mvn clean compile
```

## 테스트 실행 방법 (Execution Guide)
각 DBMS 별로 데이터 생성(Build) 및 부하 테스트(Execute)를 위한 스크립트가 준비되어 있습니다.
테스트 수행 전, 각 `tpcc_<dbms>_build.xml` 파일 내의 `DBUrl`, `username`, `password`가 정확한지 확인하십시오.

### 1. MySQL
```bash
# 데이터 생성 및 로딩
./start_build_mysql.sh

# 부하 테스트 실행
./start_exec_mysql.sh
```

### 2. PostgreSQL
```bash
# 데이터 생성 및 로딩
./start_build_pg.sh

# 부하 테스트 실행
./start_exec_pg.sh
```
*참고: PostgreSQL 서버의 `pg_hba.conf` 설정이 클라이언트 접속을 허용하는지 확인하십시오.*

### 3. Oracle
```bash
# 데이터 생성 및 로딩
./start_build_oracle.sh

# 부하 테스트 실행
./start_exec_oracle.sh
```

### 4. Tibero
```bash
# 데이터 생성 및 로딩
./start_build_tibero.sh

# 부하 테스트 실행
./start_exec_tibero.sh
```

### 5. MSSQL (SQL Server)
```bash
# 데이터 생성 및 로딩
./start_build_mssql.sh

# 부하 테스트 실행
./start_exec_mssql.sh
```

### 6. DB2
```bash
# 데이터 생성 및 로딩
./start_build_db2.sh

# 부하 테스트 실행
./start_exec_db2.sh
```

## 설정 파일 (Configuration)
각 DBMS의 접속 정보 및 테스트 파라미터(Time, Rate, Terminals 등)는 다음 파일에서 수정할 수 있습니다.
- MySQL: `tpcc_mysql_build.xml`
- PostgreSQL: `tpcc_postgres_build.xml`
- Oracle: `tpcc_ora_build.xml`
- Tibero: `tpcc_tibero_build.xml`
- MSSQL: `tpcc_mssql_build.xml`
- DB2: `tpcc_db2_build.xml`

## 문제 해결 (Troubleshooting)
- **Connection Error**: DB URL, IP, 포트, 방화벽 설정을 확인하십시오.
- **Table Not Found during Build**: `start_build_*.sh` 스크립트는 기존 테이블을 삭제(Drop)하고 재생성합니다. 최초 실행 시 Drop 단계에서 오류가 발생할 수 있으나 무시하고 진행됩니다.
- **Authentication Error**: 각 XML 설정 파일의 username/password를 확인하십시오.

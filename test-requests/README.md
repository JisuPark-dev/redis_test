# 동시성 제어 테스트 가이드

## 빠른 시작

### 1. 인프라 준비
```bash
# MySQL 시작 (Docker 사용 시)
docker run -d --name mysql-test -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=1234qwer!! \
  -e MYSQL_DATABASE=redis_test \
  mysql:8.0

# Redis 시작 (Docker 사용 시)
docker run -d --name redis-test -p 6379:6379 redis:7-alpine
```

### 2. 애플리케이션 실행
```bash
./gradlew bootRun
```

### 3. 자동 테스트 실행
```bash
cd test-requests
./06-concurrent-test-script.sh
```

## 테스트 방법

### 방법 1: HTTP 파일 사용 (IntelliJ/VSCode)
1. `test-requests/` 폴더의 `.http` 파일들을 열기
2. 각 요청을 수동으로 실행하거나 동시에 실행
3. 결과 비교

### 방법 2: cURL 사용
```bash
# 동시 실행 예시
curl -X PATCH "http://localhost:8080/api/no-lock/products/4/stock" \
  -H "Content-Type: application/json" -d '{"quantity": -1}' &
curl -X PATCH "http://localhost:8080/api/no-lock/products/4/stock" \
  -H "Content-Type: application/json" -d '{"quantity": -1}' &
wait
```

### 방법 3: 자동화 스크립트
```bash
./test-requests/06-concurrent-test-script.sh
```

## 초기 테스트 데이터

서버 시작 시 자동으로 생성됩니다:

### product_optlock 테이블 (낙관적 락)
| 상품 ID | SKU | 이름 | 초기 재고 | API 경로 |
|---------|-----|------|-----------|----------|
| 1 | OPT-001 | 낙관적 락 테스트 상품 | 1 | `/api/products` |

### product_plain 테이블 (비관적 락, Redis 분산락, 락 없음)  
| 상품 ID | SKU | 이름 | 초기 재고 | 테스트 목적 |
|---------|-----|------|-----------|-------------|
| 1 | PES-001 | 비관적 락 테스트 상품 | 1 | 비관적 락 테스트 |
| 2 | REDIS-001 | Redis 분산락 테스트 상품 | 1 | Redis 분산락 테스트 |
| 3 | NO-LOCK-001 | 락 없는 테스트 상품 | 5 | Race Condition 테스트 |

## 예상 결과

### 1. 락 없는 상태 (Race Condition)
- **테스트**: 재고 5개에서 동시에 5번 -1 요청
- **예상**: 최종 재고가 0이 아닌 다른 값 (1, 2, 3 등)
- **원인**: Lost Update 문제

### 2. 낙관적 락
- **테스트**: 동일한 version으로 동시 업데이트
- **예상**: 하나는 성공(200), 하나는 실패(409 Conflict)
- **장점**: 읽기 성능 좋음, 충돌 시에만 재시도

### 3. 비관적 락 (SELECT FOR UPDATE)
- **테스트**: 재고 1개에서 동시에 2번 -1 요청  
- **예상**: 순차 처리되어 하나는 성공, 하나는 재고 부족 에러
- **특징**: 데이터베이스 레벨 락, 대기 시간 발생

### 4. Redis 분산락
- **테스트**: 재고 1개에서 동시에 2번 -1 요청
- **예상**: 순차 처리되어 하나는 성공, 하나는 재고 부족 에러  
- **장점**: 분산 환경에서 동작, 락 타임아웃 설정 가능

## 로그 모니터링

애플리케이션 실행 중 다음 로그를 확인하세요:

```log
# 초기 데이터 로딩
INFO  - 낙관적 락 테스트 상품 생성: OPT-001

# Redis 락 동작
DEBUG - Lock acquired for key: product:stock:lock:3
DEBUG - Lock released for key: product:stock:lock:3

# 재고 변경
INFO  - 락 없이 재고 업데이트: 상품ID=4, 변경량=-1, 최종재고=4
```

## 문제 해결

### MySQL 연결 실패
```bash
# MySQL 프로세스 확인
brew services list | grep mysql

# 또는 Docker로 실행
docker run -d --name mysql-test -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=1234qwer!! \
  -e MYSQL_DATABASE=redis_test \
  mysql:8.0
```

### Redis 연결 실패
```bash
# Redis 프로세스 확인  
brew services list | grep redis

# 또는 Docker로 실행
docker run -d --name redis-test -p 6379:6379 redis:7-alpine
```

### 테스트 데이터 리셋
```sql
# MySQL에서 테이블 초기화
TRUNCATE TABLE product_optlock;
TRUNCATE TABLE product_plain;

# 애플리케이션 재시작하면 자동으로 테스트 데이터 재생성
```
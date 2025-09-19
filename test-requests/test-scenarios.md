# 동시성 제어 테스트 시나리오

## 준비사항
1. MySQL 실행 (localhost:3306)
2. Redis 실행 (localhost:6379)
3. 애플리케이션 시작: `./gradlew bootRun`

## 초기 데이터 확인
서버 시작 시 자동으로 생성되는 테스트 상품들:

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

**API 접근 경로:**
- 비관적 락: `/api/pessimistic/products/{id}`
- Redis 분산락: `/api/redis-lock/products/{id}`  
- 락 없음: `/api/no-lock/products/{id}/stock` (재고 변경만)

## 테스트 시나리오

### 방법 1: 한 번의 API 호출로 100개 스레드 동시 실행

#### 1. 재고 초기화 (100개로 설정)
```bash
# 낙관적 락 테스트 상품 (1번)
curl -X PUT "http://localhost:8080/api/products/1" \
  -H "Content-Type: application/json" \
  -d '{"name": "낙관적 락 테스트 상품", "priceKrw": 10000, "stockQty": 100, "version": 0}'

# 락 없는 테스트 상품 (3번)
curl -X PUT "http://localhost:8080/api/pessimistic/products/3" \
  -H "Content-Type: application/json" \
  -d '{"name": "락 없는 테스트 상품", "priceKrw": 5000, "stockQty": 100, "version": 0}'

# 비관적 락 테스트 상품 (1번)  
curl -X PUT "http://localhost:8080/api/pessimistic/products/1" \
  -H "Content-Type: application/json" \
  -d '{"name": "비관적 락 테스트 상품", "priceKrw": 20000, "stockQty": 100, "version": 0}'

# Redis 분산락 테스트 상품 (2번)
curl -X PUT "http://localhost:8080/api/redis-lock/products/2" \
  -H "Content-Type: application/json" \
  -d '{"name": "Redis 분산락 테스트 상품", "priceKrw": 30000, "stockQty": 100, "version": 0}'
```

#### 2. 동시성 테스트 (100개 스레드가 동시에 -1 실행)

**낙관적 락 테스트**:
```bash
curl -X POST "http://localhost:8080/api/products/1/concurrent-test" \
  -H "Content-Type: application/json" \
  -d '{"quantity": -1, "concurrentCount": 100}'
```

**락 없는 상태 Race Condition 테스트**:
```bash
curl -X POST "http://localhost:8080/api/no-lock/products/3/concurrent-test" \
  -H "Content-Type: application/json" \
  -d '{"quantity": -1, "concurrentCount": 100}'
```

**비관적 락 테스트**:
```bash
curl -X POST "http://localhost:8080/api/pessimistic/products/1/concurrent-test" \
  -H "Content-Type: application/json" \
  -d '{"quantity": -1, "concurrentCount": 100}'
```

**Redis 분산락 테스트**:
```bash  
curl -X POST "http://localhost:8080/api/redis-lock/products/2/concurrent-test" \
  -H "Content-Type: application/json" \
  -d '{"quantity": -1, "concurrentCount": 100}'
```

### 4. 성능 비교 테스트

**목표**: 각 락 메커니즘의 성능 차이 확인

```bash
echo "Pessimistic Lock Test 시작: $(date '+%Y-%m-%d %H:%M:%S.%3N')"
time for i in {1..100}; do
  curl -s -X PATCH "http://localhost:8080/api/pessimistic/products/1/stock" \
    -H "Content-Type: application/json" \
    -d '{"quantity": 0}' > /dev/null
done
echo "Pessimistic Lock Test 종료: $(date '+%Y-%m-%d %H:%M:%S.%3N')"

echo "Redis Lock Test 시작: $(date '+%Y-%m-%d %H:%M:%S.%3N')"
time for i in {1..100}; do
  curl -s -X PATCH "http://localhost:8080/api/redis-lock/products/2/stock" \
    -H "Content-Type: application/json" \
    -d '{"quantity": 0}' > /dev/null
done
echo "Redis Lock Test 종료: $(date '+%Y-%m-%d %H:%M:%S.%3N')"

echo "Optimistic Lock Test 시작: $(date '+%Y-%m-%d %H:%M:%S.%3N')"
time for i in {1..100}; do
  curl -s -X PATCH "http://localhost:8080/api/products/1/stock" \
    -H "Content-Type: application/json" \
    -d '{"quantity": 0}' > /dev/null
done
echo "Optimistic Lock Test 종료: $(date '+%Y-%m-%d %H:%M:%S.%3N')"

```

## 테스트 결과 예상

### 동시성 테스트 결과 비교

| 락 타입 | 성공률 | 최종 재고 | 특징 |
|---------|--------|-----------|------|
| **락 없음** | 부분 성공 | ≠ 0 | Lost Update 발생 |
| **낙관적 락** | 100% | 0 | 충돌 시 409 에러 |
| **비관적 락** | 100% | 0 | 스레드풀 10개로 순차 처리 |
| **Redis 분산락** | 100% | 0 | 분산 환경 대응 |

### 성능 비교 (100번 순차 요청)
1. **낙관적 락**: 가장 빠름 (읽기 성능 우수)
2. **Redis 분산락**: 중간 (네트워크 오버헤드)
3. **비관적 락**: 가장 느림 (DB 락 대기)


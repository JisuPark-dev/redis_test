#!/bin/bash

# 개선된 동시성 테스트 스크립트 (API 한 번 호출로 100개 스레드 실행)
# 사용법: ./08-concurrent-test-script-new.sh

echo "=== 개선된 동시성 제어 테스트 시작 ==="

# 색깔 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 서버 상태 확인
echo -e "${BLUE}서버 상태 확인 중...${NC}"
if ! curl -s http://localhost:8080/api/products > /dev/null; then
    echo -e "${RED}서버가 실행되지 않았습니다. 먼저 './gradlew bootRun' 으로 서버를 시작하세요.${NC}"
    exit 1
fi
echo -e "${GREEN}서버 연결 성공${NC}"

# 0. 재고 초기화 (100개로 설정)
echo -e "\n${YELLOW}=== 0. 재고 초기화 (100개로 설정) ===${NC}"

echo "락 없는 테스트 상품 재고 초기화..."
curl -s -X PUT "http://localhost:8080/api/pessimistic/products/3" \
     -H "Content-Type: application/json" \
     -d '{"name": "락 없는 테스트 상품", "priceKrw": 5000, "stockQty": 100, "version": 0}' > /dev/null

echo "비관적 락 테스트 상품 재고 초기화..."
curl -s -X PUT "http://localhost:8080/api/pessimistic/products/1" \
     -H "Content-Type: application/json" \
     -d '{"name": "비관적 락 테스트 상품", "priceKrw": 20000, "stockQty": 100, "version": 0}' > /dev/null

echo "Redis 분산락 테스트 상품 재고 초기화..."
curl -s -X PUT "http://localhost:8080/api/redis-lock/products/2" \
     -H "Content-Type: application/json" \
     -d '{"name": "Redis 분산락 테스트 상품", "priceKrw": 30000, "stockQty": 100, "version": 0}' > /dev/null

echo -e "${GREEN}재고 초기화 완료${NC}"

# 1. 락 없는 상태 테스트 (100개 스레드 동시 실행)
echo -e "\n${YELLOW}=== 1. 락 없는 상태 동시성 테스트 ===${NC}"
echo "100개 스레드가 동시에 -1 요청 (예상: Lost Update 발생)"

NO_LOCK_RESULT=$(curl -s -X POST "http://localhost:8080/api/no-lock/products/3/concurrent-test" \
     -H "Content-Type: application/json" \
     -d '{"quantity": -1, "concurrentCount": 100}')

echo -e "${RED}락 없는 테스트 결과:${NC}"
echo "$NO_LOCK_RESULT" | jq '.'

# 2. 비관적 락 테스트
echo -e "\n${YELLOW}=== 2. 비관적 락 동시성 테스트 ===${NC}"
echo "100개 스레드가 동시에 -1 요청 (예상: 순차 처리)"

PESSIMISTIC_RESULT=$(curl -s -X POST "http://localhost:8080/api/pessimistic/products/1/concurrent-test" \
     -H "Content-Type: application/json" \
     -d '{"quantity": -1, "concurrentCount": 100}')

echo -e "${GREEN}비관적 락 테스트 결과:${NC}"
echo "$PESSIMISTIC_RESULT" | jq '.'

# 3. Redis 분산락 테스트
echo -e "\n${YELLOW}=== 3. Redis 분산락 동시성 테스트 ===${NC}"
echo "100개 스레드가 동시에 -1 요청 (예상: 순차 처리)"

REDIS_RESULT=$(curl -s -X POST "http://localhost:8080/api/redis-lock/products/2/concurrent-test" \
     -H "Content-Type: application/json" \
     -d '{"quantity": -1, "concurrentCount": 100}')

echo -e "${GREEN}Redis 분산락 테스트 결과:${NC}"
echo "$REDIS_RESULT" | jq '.'

# 4. 결과 요약
echo -e "\n${BLUE}=== 4. 결과 요약 ===${NC}"

NO_LOCK_FINAL=$(echo "$NO_LOCK_RESULT" | jq -r '.finalStockQty')
NO_LOCK_SUCCESS=$(echo "$NO_LOCK_RESULT" | jq -r '.successCount')
NO_LOCK_TIME=$(echo "$NO_LOCK_RESULT" | jq -r '.totalExecutionTimeMs')

PESSIMISTIC_FINAL=$(echo "$PESSIMISTIC_RESULT" | jq -r '.finalStockQty')
PESSIMISTIC_SUCCESS=$(echo "$PESSIMISTIC_RESULT" | jq -r '.successCount')
PESSIMISTIC_TIME=$(echo "$PESSIMISTIC_RESULT" | jq -r '.totalExecutionTimeMs')

REDIS_FINAL=$(echo "$REDIS_RESULT" | jq -r '.finalStockQty')
REDIS_SUCCESS=$(echo "$REDIS_RESULT" | jq -r '.successCount')
REDIS_TIME=$(echo "$REDIS_RESULT" | jq -r '.totalExecutionTimeMs')

echo -e "┌─────────────────┬─────────────┬─────────────┬─────────────┐"
echo -e "│ 락 타입         │ 최종 재고   │ 성공 요청   │ 실행시간(ms)│"
echo -e "├─────────────────┼─────────────┼─────────────┼─────────────┤"
echo -e "│ ${RED}락 없음${NC}         │ ${RED}${NO_LOCK_FINAL}${NC}          │ ${NO_LOCK_SUCCESS}          │ ${NO_LOCK_TIME}ms       │"
echo -e "│ ${GREEN}비관적 락${NC}       │ ${GREEN}${PESSIMISTIC_FINAL}${NC}           │ ${PESSIMISTIC_SUCCESS}          │ ${PESSIMISTIC_TIME}ms      │"
echo -e "│ ${GREEN}Redis 분산락${NC}   │ ${GREEN}${REDIS_FINAL}${NC}           │ ${REDIS_SUCCESS}          │ ${REDIS_TIME}ms       │"
echo -e "└─────────────────┴─────────────┴─────────────┴─────────────┘"

echo -e "\n${BLUE}=== 분석 ===${NC}"
if [ "$NO_LOCK_FINAL" != "0" ] || [ "$NO_LOCK_SUCCESS" != "100" ]; then
    echo -e "${RED}✗ 락 없는 상태: Lost Update 발생 (예상됨)${NC}"
else
    echo -e "${YELLOW}⚠ 락 없는 상태: 예상과 다르게 정상 동작 (재시도 권장)${NC}"
fi

if [ "$PESSIMISTIC_FINAL" = "0" ] && [ "$PESSIMISTIC_SUCCESS" = "100" ]; then
    echo -e "${GREEN}✓ 비관적 락: 정상 동작${NC}"
else
    echo -e "${RED}✗ 비관적 락: 문제 발생${NC}"
fi

if [ "$REDIS_FINAL" = "0" ] && [ "$REDIS_SUCCESS" = "100" ]; then
    echo -e "${GREEN}✓ Redis 분산락: 정상 동작${NC}"
else
    echo -e "${RED}✗ Redis 분산락: 문제 발생${NC}"
fi

echo -e "\n${BLUE}=== 테스트 완료 ===${NC}"
echo -e "애플리케이션 로그에서 상세한 동작 과정을 확인하세요."
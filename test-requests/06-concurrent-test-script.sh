#!/bin/bash

# 동시성 테스트 스크립트
# 사용법: ./06-concurrent-test-script.sh

echo "=== 동시성 제어 테스트 시작 ==="

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

# 1. 락 없는 상태 테스트 (Race Condition)
echo -e "\n${YELLOW}=== 1. 락 없는 상태 Race Condition 테스트 ===${NC}"
echo "5개 재고에서 동시에 5번 -1 요청 (예상: race condition 발생)"

# 백그라운드로 동시 요청 (product_plain 테이블 3번 상품)
for i in {1..5}; do
    curl -s -X PATCH "http://localhost:8080/api/no-lock/products/3/stock" \
         -H "Content-Type: application/json" \
         -d '{"quantity": -1}' &
done
wait

# 결과 확인 (product_plain 테이블 3번 상품)
NO_LOCK_RESULT=$(curl -s "http://localhost:8080/api/pessimistic/products/3" | jq -r '.stockQty')
echo -e "락 없는 테스트 최종 재고: ${RED}${NO_LOCK_RESULT}${NC} (예상: 0, 실제: race condition으로 다를 수 있음)"

# 2. 비관적 락 테스트
echo -e "\n${YELLOW}=== 2. 비관적 락 테스트 ===${NC}"
echo "1개 재고에서 동시에 2번 -1 요청 (예상: 순차 처리로 음수 방지)"

# product_plain 테이블 1번 상품
for i in {1..2}; do
    curl -s -X PATCH "http://localhost:8080/api/pessimistic/products/1/stock" \
         -H "Content-Type: application/json" \
         -d '{"quantity": -1}' &
done
wait

PESSIMISTIC_RESULT=$(curl -s "http://localhost:8080/api/pessimistic/products/1" | jq -r '.stockQty')
echo -e "비관적 락 테스트 최종 재고: ${GREEN}${PESSIMISTIC_RESULT}${NC} (예상: -1 또는 에러)"

# 3. Redis 분산락 테스트  
echo -e "\n${YELLOW}=== 3. Redis 분산락 테스트 ===${NC}"
echo "1개 재고에서 동시에 2번 -1 요청 (예상: 순차 처리로 음수 방지)"

# product_plain 테이블 2번 상품
for i in {1..2}; do
    curl -s -X PATCH "http://localhost:8080/api/redis-lock/products/2/stock" \
         -H "Content-Type: application/json" \
         -d '{"quantity": -1}' &
done
wait

REDIS_RESULT=$(curl -s "http://localhost:8080/api/redis-lock/products/2" | jq -r '.stockQty')
echo -e "Redis 분산락 테스트 최종 재고: ${GREEN}${REDIS_RESULT}${NC} (예상: -1 또는 에러)"

# 4. 낙관적 락 테스트
echo -e "\n${YELLOW}=== 4. 낙관적 락 테스트 ===${NC}"
echo "동일한 version으로 동시 업데이트 (예상: 하나는 성공, 하나는 409 Conflict)"

# 현재 version 조회
CURRENT_VERSION=$(curl -s "http://localhost:8080/api/products/1" | jq -r '.version')
echo "현재 version: ${CURRENT_VERSION}"

# 동일한 version으로 동시 업데이트
curl -s -X PUT "http://localhost:8080/api/products/1" \
     -H "Content-Type: application/json" \
     -d "{\"name\": \"수정1\", \"priceKrw\": 11000, \"stockQty\": 10, \"version\": ${CURRENT_VERSION}}" &

curl -s -X PUT "http://localhost:8080/api/products/1" \
     -H "Content-Type: application/json" \
     -d "{\"name\": \"수정2\", \"priceKrw\": 12000, \"stockQty\": 20, \"version\": ${CURRENT_VERSION}}" &

wait

OPTIMISTIC_RESULT=$(curl -s "http://localhost:8080/api/products/1" | jq -r '.name')
echo -e "낙관적 락 테스트 결과: ${GREEN}${OPTIMISTIC_RESULT}${NC}"

# 성능 테스트
echo -e "\n${YELLOW}=== 5. 성능 비교 테스트 ===${NC}"
echo "각 방식으로 10번씩 요청하여 성능 측정"

echo -n "비관적 락 성능 테스트... "
PESSIMISTIC_TIME=$(time (
    for i in {1..10}; do
        curl -s -X PATCH "http://localhost:8080/api/pessimistic/products/1/stock" \
             -H "Content-Type: application/json" \
             -d '{"quantity": 0}' > /dev/null
    done
) 2>&1 | grep real | awk '{print $2}')
echo -e "${GREEN}완료 (${PESSIMISTIC_TIME})${NC}"

echo -n "Redis 분산락 성능 테스트... "
REDIS_TIME=$(time (
    for i in {1..10}; do
        curl -s -X PATCH "http://localhost:8080/api/redis-lock/products/2/stock" \
             -H "Content-Type: application/json" \
             -d '{"quantity": 0}' > /dev/null
    done
) 2>&1 | grep real | awk '{print $2}')
echo -e "${GREEN}완료 (${REDIS_TIME})${NC}"

echo -e "\n${BLUE}=== 테스트 완료 ===${NC}"
echo -e "애플리케이션 로그와 데이터베이스를 확인하여 상세한 동작을 관찰하세요."
echo -e "재고가 음수가 되거나 예상과 다른 결과가 나온다면 동시성 이슈가 발생한 것입니다."
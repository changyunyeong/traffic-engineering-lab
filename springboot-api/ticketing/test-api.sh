#!/bin/bash

# 티켓팅 시스템 API 테스트 스크립트
# 실행: chmod +x test-api.sh && ./test-api.sh

BASE_URL="http://localhost:8080"

# 색상 코드
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "티켓팅 시스템 API 테스트 시작"
echo "=================================="
echo ""

# 1. 헬스 체크
echo "헬스 체크..."
response=$(curl -s -o /dev/null -w "%{http_code}" ${BASE_URL}/actuator/health)
if [ $response -eq 200 ]; then
    echo -e "${GREEN}서버 정상 (200 OK)${NC}"
else
    echo -e "${RED}서버 응답 없음 (${response})${NC}"
    exit 1
fi
echo ""

# 2. 전체 이벤트 조회
echo "전체 이벤트 조회..."
events=$(curl -s ${BASE_URL}/api/v1/events)
event_count=$(echo $events | jq '.data.content | length')
echo -e "${GREEN}이벤트 ${event_count}개 조회 성공${NC}"
echo ""

# 3. 특정 이벤트 조회
echo "이벤트 상세 조회 (ID: 1)..."
event=$(curl -s ${BASE_URL}/api/v1/events/1)
title=$(echo $event | jq -r '.data.title')
echo -e "${GREEN}이벤트: ${title}${NC}"
echo ""

# 4. 티켓 조회
echo "이벤트 티켓 조회 (Event ID: 1)..."
tickets=$(curl -s ${BASE_URL}/api/v1/tickets/event/1)
ticket_count=$(echo $tickets | jq '.data | length')
echo -e "${GREEN}티켓 ${ticket_count}개 조회 성공${NC}"
echo ""

# 5. 티켓 예약 (단일)
echo "티켓 예약 테스트 (단일)..."
reservation=$(curl -s -X POST ${BASE_URL}/api/v1/reservations \
  -H "Content-Type: application/json" \
  -d '{"ticketId": 1, "userId": 1}')
  
reservation_id=$(echo $reservation | jq -r '.data.id')
if [ "$reservation_id" != "null" ] && [ "$reservation_id" != "" ]; then
    echo -e "${GREEN}예약 성공 (ID: ${reservation_id})${NC}"
else
    echo -e "${YELLOW}예약 실패 (이미 예약했거나 매진)${NC}"
fi
echo ""

# 6. 동시 예약 테스트
echo "동시 예약 테스트 ..."
success_count=0
for i in {1..10}; do
    response=$(curl -s -X POST ${BASE_URL}/api/v1/reservations \
      -H "Content-Type: application/json" \
      -d "{\"ticketId\": 2, \"userId\": $i}" &)
done
wait

echo -e "${YELLOW} 10명 동시 예약 요청 완료${NC}"
echo ""

# 7. 재고 확인
echo "재고 확인 (Ticket ID: 3)..."
ticket=$(curl -s ${BASE_URL}/api/v1/tickets/3)
stock=$(echo $ticket | jq -r '.data.stock')
echo -e "${GREEN} 현재 재고: ${stock}개${NC}"
echo ""

# 8. 대기열 테스트
echo "대기열 진입 테스트..."
queue=$(curl -s -X POST "${BASE_URL}/api/v1/queue/tickets/1?userId=999")
token=$(echo $queue | jq -r '.data.token')
position=$(echo $queue | jq -r '.data.position')
if [ "$token" != "null" ]; then
    echo -e "${GREEN}대기열 진입 성공 (순번: ${position})${NC}"
else
    echo -e "${RED}대기열 진입 실패${NC}"
fi
echo ""

# 9. Prometheus 메트릭 확인
echo "rometheus 메트릭 확인..."
metrics=$(curl -s ${BASE_URL}/actuator/prometheus | grep http_server_requests_seconds_count | head -1)
if [ -n "$metrics" ]; then
    echo -e "${GREEN}Prometheus 메트릭 수집 중${NC}"
else
    echo -e "${YELLOW}Prometheus 메트릭 없음${NC}"
fi
echo ""

# 10. 최종 결과
echo "=================================="
echo -e "${GREEN} 테스트 완료!${NC}"
echo ""
echo "Swagger UI: http://localhost:8080/swagger-ui.html"
echo "Prometheus: http://localhost:9090"
echo "Grafana: http://localhost:3000"
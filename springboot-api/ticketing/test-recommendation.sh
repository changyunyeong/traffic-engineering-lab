#!/bin/bash
# chmod +x test-recommendation.sh

BASE_URL="http://localhost:8080"
FASTAPI_URL="http://localhost:8000"

echo "추천 시스템 테스트 시작"
echo "======================================"

# 1. FastAPI 상태 확인
echo "FastAPI 상태 확인..."
curl -s ${FASTAPI_URL}/health | jq
echo ""

# 2. Spring Boot에서 FastAPI 상태 확인
echo "Spring Boot → FastAPI 연결 확인..."
curl -s ${BASE_URL}/api/v1/recommendations/health | jq
echo ""

# 3. 추천 모델 학습
echo "추천 모델 학습 트리거..."
curl -s -X POST "${FASTAPI_URL}/api/v1/recommendations/train?force_retrain=true" | jq
echo ""
sleep 3

# 4. 사용자 5번에게 추천 받기
echo "사용자 5번 추천 받기..."
curl -s ${BASE_URL}/api/v1/recommendations/5?limit=5 | jq
echo ""

# 5. 캐시 확인 (두 번째 호출은 빨라야 함)
echo "캐시 확인 (두 번째 호출)..."
time curl -s ${BASE_URL}/api/v1/recommendations/5?limit=5 > /dev/null
echo ""

# 6. 캐시 삭제
echo "캐시 삭제..."
curl -s -X DELETE ${BASE_URL}/api/v1/recommendations/cache/1 | jq
echo ""

# 7. 다른 사용자 추천
echo "사용자 8번 추천 받기..."
curl -s ${BASE_URL}/api/v1/recommendations/8?limit=3 | jq
echo ""

echo "======================================"
echo "테스트 완료!"
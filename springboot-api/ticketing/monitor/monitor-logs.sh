#!/bin/bash

# 통합 로그 모니터링 스크립트
# 실행: chmod +x monitor/monitor-logs.sh && ./monitor/monitor-logs.sh

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

show_help() {
    echo "사용법: ./monitor-logs.sh [옵션]"
    echo ""
    echo "옵션:"
    echo "  all         - 모든 로그 (기본값)"
    echo "  error       - 에러 로그만"
    echo "  reservation - 예약 관련 로그만"
    echo "  slow        - 느린 요청 (>1초)"
    echo "  redis       - Redis 관련 로그만"
    echo ""
    echo "예시:"
    echo "  ./monitor-logs.sh error"
    echo "  ./monitor-logs.sh reservation"
}

MODE=${1:-all}

case $MODE in
    error)
        echo -e "${RED} 에러 로그 모니터링${NC}"
        docker-compose logs -f springboot-api | grep -i --line-buffered "ERROR"
        ;;

    reservation)
        echo -e "${GREEN} 예약 로그 모니터링${NC}"
        docker-compose logs -f springboot-api | grep -i --line-buffered -E "Reservation|reserve|ticket"
        ;;

    slow)
        echo -e "${YELLOW} 느린 요청 모니터링 (>1초)${NC}"
        docker-compose logs -f springboot-api | grep -i --line-buffered "duration" | awk '{if ($NF > 1000) print}'
        ;;

    redis)
        echo -e "${BLUE} Redis 관련 로그${NC}"
        docker-compose logs -f springboot-api | grep -i --line-buffered "redis\|cache"
        ;;

    help)
        show_help
        ;;

    all|*)
        echo -e "${GREEN} 전체 로그 모니터링${NC}"
        echo "Ctrl+C로 종료"
        docker-compose logs -f springboot-api
        ;;
esac
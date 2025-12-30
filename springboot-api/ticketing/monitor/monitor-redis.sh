#!/bin/bash

# Redis 모니터링 스크립트
# 실행: chmod +x monitor/monitor-redis.sh && ./monitor/monitor-redis.sh

echo "Redis 모니터링 시작"
echo "Press Ctrl+C to stop"
echo ""

while true; do
    clear
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "Redis 실시간 모니터링 - $(date '+%H:%M:%S')"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    # 1. 기본 정보
    echo ""
    echo "서버 정보:"
    docker-compose exec -T redis-master redis-cli INFO server | grep -E "redis_version|uptime_in_seconds"

    # 2. 메모리 사용량
    echo ""
    echo "메모리 사용량:"
    docker-compose exec -T redis-master redis-cli INFO memory | grep -E "used_memory_human|used_memory_peak_human|maxmemory_human"

    # 3. 연결 수
    echo ""
    echo "연결 정보:"
    docker-compose exec -T redis-master redis-cli INFO clients | grep -E "connected_clients|blocked_clients"

    # 4. 성능 통계
    echo ""
    echo "성능 통계:"
    docker-compose exec -T redis-master redis-cli INFO stats | grep -E "total_commands_processed|instantaneous_ops_per_sec|keyspace_hits|keyspace_misses"

    # 5. 키 개수
    echo ""
    echo "저장된 키:"
    docker-compose exec -T redis-master redis-cli DBSIZE

    # 6. 티켓 재고 (실시간)
    echo ""
    echo "티켓 재고 (Redis):"
    for i in {1..3} {10..11}; do
        stock=$(docker-compose exec -T redis-master redis-cli GET "ticket:stock:$i" 2>/dev/null)
        if [ ! -z "$stock" ]; then
            echo "  Ticket $i: $stock석"
        fi
    done

    # 7. 캐시 히트율 계산
    echo ""
    echo "캐시 성능:"
    hits=$(docker-compose exec -T redis-master redis-cli INFO stats | grep keyspace_hits | cut -d: -f2 | tr -d '\r')
    misses=$(docker-compose exec -T redis-master redis-cli INFO stats | grep keyspace_misses | cut -d: -f2 | tr -d '\r')
    if [ ! -z "$hits" ] && [ ! -z "$misses" ]; then
        total=$((hits + misses))
        if [ $total -gt 0 ]; then
            hit_rate=$(echo "scale=2; $hits * 100 / $total" | bc)
            echo "  히트율: ${hit_rate}%"
        fi
    fi

    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    sleep 2
done
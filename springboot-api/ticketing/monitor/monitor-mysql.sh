#!/bin/bash

# MySQL 모니터링 스크립트
# 실행: chmod +x monitor/monitor-mysql.sh && ./monitor/monitor-mysql.sh

echo "MySQL 모니터링 시작"
echo "Press Ctrl+C to stop"
echo ""

while true; do
    clear
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "MySQL 실시간 모니터링 - $(date '+%H:%M:%S')"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    # 1. 연결 정보
    echo ""
    echo "연결 정보:"
    docker-compose exec -T mysql mysql -uadmin -proot -e "
        SHOW STATUS LIKE 'Threads_%';
        SHOW STATUS LIKE 'Max_used_connections';
    " 2>/dev/null | tail -n +2

    # 2. 쿼리 처리량
    echo ""
    echo "쿼리 통계:"
    docker-compose exec -T mysql mysql -uadmin -proot -e "
        SHOW GLOBAL STATUS LIKE 'Queries';
        SHOW GLOBAL STATUS LIKE 'Questions';
        SHOW GLOBAL STATUS LIKE 'Com_select';
        SHOW GLOBAL STATUS LIKE 'Com_insert';
        SHOW GLOBAL STATUS LIKE 'Com_update';
        SHOW GLOBAL STATUS LIKE 'Com_delete';
    " 2>/dev/null | tail -n +2

    # 3. InnoDB 버퍼 풀
    echo ""
    echo " nnoDB 버퍼 풀:"
    docker-compose exec -T mysql mysql -uadmin -proot -e "
        SHOW STATUS LIKE 'Innodb_buffer_pool_%';
    " 2>/dev/null | grep -E "size|pages_free|pages_data" | tail -n +1

    # 4. 슬로우 쿼리
    echo ""
    echo "슬로우 쿼리:"
    docker-compose exec -T mysql mysql -uadmin -proot -e "
        SHOW GLOBAL STATUS LIKE 'Slow_queries';
    " 2>/dev/null | tail -n +2

    # 5. 현재 실행 중인 쿼리
    echo ""
    echo " 실행 중인 쿼리 (5개):"
    docker-compose exec -T mysql mysql -uadmin -proot -e "
        SELECT
            id,
            user,
            db,
            command,
            time,
            LEFT(info, 50) as query
        FROM information_schema.processlist
        WHERE command != 'Sleep'
        ORDER BY time DESC
        LIMIT 5;
    " 2>/dev/null | tail -n +2

    # 6. 티켓 재고 (DB)
    echo ""
    echo " 티켓 재고 (MySQL):"
    docker-compose exec -T mysql mysql -uadmin -proot ticketing -e "
        SELECT
            id,
            name,
            stock,
            price
        FROM tickets
        WHERE id IN (1,2,3,10,11)
        ORDER BY id;
    " 2>/dev/null | tail -n +2

    # 7. 예약 통계
    echo ""
    echo "예약 통계:"
    docker-compose exec -T mysql mysql -uadmin -proot ticketing -e "
        SELECT
            status,
            COUNT(*) as count
        FROM reservations
        GROUP BY status;
    " 2>/dev/null | tail -n +2

    # 8. 테이블 크기
    echo ""
    echo "테이블 크기:"
    docker-compose exec -T mysql mysql -uadmin -proot ticketing -e "
        SELECT
            table_name,
            table_rows,
            ROUND(data_length/1024/1024, 2) as data_mb
        FROM information_schema.tables
        WHERE table_schema = 'ticketing'
        ORDER BY data_length DESC
        LIMIT 5;
    " 2>/dev/null | tail -n +2

    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    sleep 2
done
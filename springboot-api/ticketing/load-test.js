import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// 커스텀 메트릭
const reservationSuccessRate = new Rate('reservation_success_rate');
const reservationDuration = new Trend('reservation_duration');
const stockCheckDuration = new Trend('stock_check_duration');
const duplicateReservations = new Counter('duplicate_reservations');
const outOfStockErrors = new Counter('out_of_stock_errors');
const serverErrors = new Counter('server_errors');

// 테스트 설정
export const options = {
    scenarios: {
        // 시나리오 1: 점진적 증가 (Warm-up)
        warmup: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 50 },
                { duration: '30s', target: 100 },
            ],
            gracefulRampDown: '10s',
        },
        // 시나리오 2: 스파이크 테스트 (대기열 오픈 순간 시뮬레이션)
        spike: {
            executor: 'ramping-vus',
            startTime: '1m',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 300 },
                { duration: '30s', target: 300 },
                { duration: '10s', target: 0 },
            ],
            gracefulRampDown: '10s',
        },
        // 시나리오 3: 안정적인 부하 테스트
        load: {
            executor: 'constant-vus',
            startTime: '2m',
            vus: 200,
            duration: '2m',
        },
    },
    thresholds: {
        'http_req_duration': ['p(95)<1000', 'p(99)<2000'],
        'http_req_failed': ['rate<0.50'],  // 50% 이하 (재고 부족 포함)
        'reservation_success_rate': ['rate>0.05'],  // 5% 이상 (현실적)
        'server_errors': ['count<10'],
    },
};

const BASE_URL = 'http://localhost:8080/api/v1';
const HEALTH_URL = 'http://localhost:8080/actuator/health';

// 테스트 데이터
const TICKETS = [
    { id: 1, name: 'BTS VIP석', eventId: 1, initialStock: 100 },
    { id: 2, name: 'BTS R석', eventId: 1, initialStock: 200 },
    { id: 3, name: 'BTS S석', eventId: 1, initialStock: 200 },
    { id: 10, name: 'IU VIP석', eventId: 4, initialStock: 50 },
    { id: 11, name: 'IU R석', eventId: 4, initialStock: 150 },
];

const MAX_USER_ID = 10000;  // 10,000명의 사용자 풀

export function setup() {
    console.log('부하 테스트 시작 (현실적 시나리오)');
    console.log('테스트 대상 티켓:', TICKETS.length);
    console.log('사용자 풀: 1 ~ ' + MAX_USER_ID);

    const healthRes = http.get(HEALTH_URL);

    if (healthRes.status !== 200) {
        console.error('서버 응답:', healthRes.body);
        throw new Error('서버가 준비되지 않았습니다');
    }

    console.log('서버 정상');

    // 초기 재고 확인
    console.log('\n 초기 재고:');
    TICKETS.forEach(ticket => {
        const ticketRes = http.get(`${BASE_URL}/tickets/${ticket.id}`);
        if (ticketRes.status === 200) {
            try {
                const body = JSON.parse(ticketRes.body);
                console.log(`  - ${ticket.name}: ${body.data.stock}석`);
            } catch (e) {
                console.warn(`  - ${ticket.name}: 재고 확인 실패`);
            }
        }
    });

    return {
        startTime: new Date().toISOString(),
        initialStocks: TICKETS.reduce((acc, t) => {
            acc[t.id] = t.initialStock;
            return acc;
        }, {})
    };
}

export default function (data) {
    // 매번 랜덤한 userId 생성 (1~10,000)
    const userId = Math.floor(Math.random() * MAX_USER_ID) + 1;

    // 인기 티켓에 가중치 부여 (BTS가 더 인기)
    const weights = [0.3, 0.3, 0.2, 0.1, 0.1];  // BTS 80%, IU 20%
    const rand = Math.random();
    let ticketIndex = 0;
    let cumulative = 0;

    for (let i = 0; i < weights.length; i++) {
        cumulative += weights[i];
        if (rand < cumulative) {
            ticketIndex = i;
            break;
        }
    }

    const ticket = TICKETS[ticketIndex];

    group('티켓 예약 플로우', function () {
        // 1. 이벤트 조회
        group('이벤트 상세 조회', function () {
            const eventRes = http.get(`${BASE_URL}/events/${ticket.eventId}`);
            check(eventRes, {
                '이벤트 조회 성공': (r) => r.status === 200,
            });
            sleep(0.3);  // 짧게
        });

        // 2. 티켓 목록 조회
        group('티켓 재고 확인', function () {
            const start = Date.now();
            const ticketRes = http.get(`${BASE_URL}/tickets/event/${ticket.eventId}/available`);
            stockCheckDuration.add(Date.now() - start);

            check(ticketRes, {
                '티켓 조회 성공': (r) => r.status === 200,
                '재고 확인 가능': (r) => {
                    try {
                        const body = JSON.parse(r.body);
                        return body.data && Array.isArray(body.data);
                    } catch (e) {
                        return false;
                    }
                },
            });
            sleep(0.5);
        });

        // 3. 티켓 예약 시도
        group('티켓 예약', function () {
            const start = Date.now();
            const payload = JSON.stringify({
                ticketId: ticket.id,
                userId: userId,
            });

            const params = {
                headers: {
                    'Content-Type': 'application/json',
                },
            };

            const reserveRes = http.post(
                `${BASE_URL}/reservations`,
                payload,
                params
            );

            const duration = Date.now() - start;
            reservationDuration.add(duration);

            check(reserveRes, {
                '예약 API 응답': (r) => r.status !== 0,
            });

            if (reserveRes.status === 200) {
                reservationSuccessRate.add(1);
                console.log(`성공: user=${userId}, ticket=${ticket.name}, ${duration}ms`);

            } else if (reserveRes.status === 409) {
                reservationSuccessRate.add(0);
                outOfStockErrors.add(1);

            } else if (reserveRes.status === 400) {
                reservationSuccessRate.add(0);
                duplicateReservations.add(1);

            } else if (reserveRes.status >= 500) {
                reservationSuccessRate.add(0);
                serverErrors.add(1);
                console.error(`서버 에러: ${reserveRes.status}, user=${userId}`);

            } else {
                reservationSuccessRate.add(0);
            }
        });
    });

    // 사용자 행동 시뮬레이션
    sleep(Math.random() * 2 + 0.5);  // 0.5~2.5초
}

export function teardown(data) {
    console.log('\n테스트 결과 요약');
    console.log('=====================================');
    console.log('시작:', data.startTime);
    console.log('종료:', new Date().toISOString());

    console.log('\n최종 재고 & 판매 현황:');
    let totalSold = 0;
    let totalRevenue = 0;

    TICKETS.forEach(ticket => {
        const stockRes = http.get(`${BASE_URL}/tickets/${ticket.id}`);
        if (stockRes.status === 200) {
            try {
                const body = JSON.parse(stockRes.body);
                const remaining = body.data.stock;
                const initial = data.initialStocks[ticket.id];
                const sold = initial - remaining;
                const soldPercent = ((sold / initial) * 100).toFixed(1);

                totalSold += sold;

                console.log(`  ${ticket.name}:`);
                console.log(`    - 초기: ${initial}석`);
                console.log(`    - 판매: ${sold}석 (${soldPercent}%)`);
                console.log(`    - 남은: ${remaining}석`);

            } catch (e) {
                console.error(`  - ${ticket.name}: 확인 실패`);
            }
        }
    });

    console.log(`\n총 판매: ${totalSold}석`);
    console.log('=====================================');
}
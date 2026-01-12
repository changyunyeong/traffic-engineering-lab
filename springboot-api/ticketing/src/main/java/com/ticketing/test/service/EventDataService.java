package com.ticketing.test.service;

import com.ticketing.domain.event.entity.Event;
import com.ticketing.domain.event.repository.EventRepository;
import com.ticketing.global.enums.Category;
import com.ticketing.global.snowflake.Snowflake;
import com.ticketing.test.dto.data.DataInitRequest;
import com.ticketing.test.dto.InitProgress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventDataService {

    private final EventRepository eventRepository;
    private final Snowflake snowflake;
    private final Random random = new Random();

    // 더미 데이터
    private static final String[] CONCERT_TITLES = {
            "BTS 월드투어", "블랙핑크 콘서트", "아이유 전국투어", "세븐틴 콘서트",
            "뉴진스 쇼케이스", "엑소 콘서트", "트와이스 팬미팅", "NCT 월드투어",
            "에스파 콘서트", "르세라핌 공연"
    };

    private static final String[] MUSICAL_TITLES = {
            "레미제라블", "오페라의 유령", "시카고", "위키드",
            "맘마미아", "캣츠", "라이온킹", "킹키부츠",
            "지킬앤하이드", "엘리자벳"
    };

    private static final String[] VENUES = {
            "고척 스카이돔", "KSPO DOME", "블루스퀘어", "예술의전당",
            "올림픽공원", "잠실종합운동장", "세종문화회관", "LG아트센터",
            "샤롯데씨어터", "대학로 예술극장"
    };

    private static final String[] SPORT_TITLES = {
            "KBO 리그", "K리그", "프로농구", "배구 챔피언십",
            "e스포츠 대회", "프로야구 플레이오프", "FA컵", "농구 올스타전",
            "테니스 토너먼트", "골프 챔피언십"
    };

    private static final String[] EXHIBITION_TITLES = {
            "현대미술 전시회", "사진전", "조각 전시회", "디자인 위크",
            "인상파 명화전", "미디어아트 전시", "한국화 특별전", "건축 비엔날레",
            "팝아트 전시회", "도자기 특별전"
    };

    private static final String[] FESTIVAL_TITLES = {
            "서울 재즈 페스티벌", "울트라 뮤직 페스티벌", "부산 국제 영화제", "한강 불꽃 축제",
            "펜타포트 록 페스티벌", "자라섬 재즈 페스티벌", "서울 랜턴 페스티벌", "보령 머드 축제",
            "부천 국제 만화축제", "전주 국제영화제"
    };

    private static final String[] OPERA_TITLES = {
            "라 트라비아타", "카르멘", "투란도트", "돈 조반니",
            "마술피리", "나비부인", "리골레토", "토스카",
            "세빌리아의 이발사", "라보엠"
    };  

    private static final String[] COMEDY_TITLES = {
            "코미디 나이트", "스탠드업 코미디 쇼", "즉흥 코미디 공연", "코미디 페스티벌",
            "개그콘서트 특집", "웃음충전소", "코미디 빅리그", "SNL 코리아 라이브",
            "코미디쇼 웃어라", "개그맨 단독 공연"
    };

    private static final String[] KIDS_TITLES = {
            "어린이 뮤지컬", "마술 공연", "인형극", "키즈 페스티벌",
            "뽀로로 쇼", "브로드웨이 키즈", "어린이 클래식 콘서트", "과학 체험 쇼",
            "동화나라 공연", "어린이 서커스"
    };

    private static final String[] THEATER_TITLES = {
            "햄릿", "로미오와 줄리엣", "맥베스", "오셀로",
            "한여름 밤의 꿈", "베니스의 상인", "리어왕", "템페스트",
            "안티고네", "욕망이라는 이름의 전차"
    };

    // 카테고리별 이미지 URL
    private static final String[] CONCERT_IMAGES = {
            "https://search.pstatic.net/common/?src=http%3A%2F%2Fimgnews.naver.net%2Fimage%2F003%2F2020%2F04%2F04%2FNISI20200403_0000506418_web_20200403170144_20200404060117382.jpg&type=sc960_832",
            "https://search.pstatic.net/common/?src=http%3A%2F%2Fblogfiles.naver.net%2FMjAyMzA5MDZfOTkg%2FMDAxNjkzOTcyMjg3NDM5.1tKKjMO41Z56gZgjvcn_s6R4ye889AJyE2mxBl8Wvc4g.uKzzU6LKBTi53qjUX_36_qRJDsNqfS4Jj01-nHVsCcAg.PNG.sakewing%2F%25BE%25C6%25C0%25CC%25C0%25AF_%25C4%25DC%25BC%25AD%25C6%25AE_%25C6%25F7%25BD%25BA%25C5%25CD.png&type=sc960_832",
            "https://search.pstatic.net/sunny/?src=https%3A%2F%2Fi.namu.wiki%2Fi%2F__nepyZ9YA2IqGUFci8I1MmIMm9uFXzr9LWrOp1lWbYZJAvM420DlwD_i8UsEmoZwk9Y8Z-T_0R2byR4HN0J4g.webp&type=sc960_832"
    };

    private static final String[] MUSICAL_IMAGES = {
            "https://search.pstatic.net/common/?src=http%3A%2F%2Fblogfiles.naver.net%2FMjAyMTExMzBfMjkw%2FMDAxNjM4MjM3MTY0OTg3.B5IXeJDnlNbuQLQXNZ1KJ31JBoR6MOQb16Vsb2pSIWog.2AKys5oHnHcjynbM-19n1LPXa012zwlOFmehDyl6qAgg.JPEG.onepiso%2Fstill_01.jpg&type=sc960_832",
            "https://search.pstatic.net/common/?src=http%3A%2F%2Fblogfiles.naver.net%2FMjAyMjExMjVfNTYg%2FMDAxNjY5MzQwNjIwNTI1.vpXeUknng6hnJrPS5NwKspVGa-t1gE2kqoDjDTzroR0g.Fz4pgvfgX7EapAe6_lBUep1SsBeqN895LgzeFEhApisg.JPEG.entcrowd%2F%25B9%25DA%25C8%25C3%253B%25A4%25A4.jpg&type=sc960_832",
            "https://search.pstatic.net/common/?src=http%3A%2F%2Fimgnews.naver.net%2Fimage%2F5535%2F2021%2F06%2F01%2F0000328774_001_20210603103412553.png&type=sc960_832"
    };

    private static final String[] SPORTS_IMAGES = {
            "https://search.pstatic.net/common/?src=http%3A%2F%2Fblogfiles.naver.net%2FMjAyMjEwMThfNDAg%2FMDAxNjY2MDg4NTAxNjU2.WYO4E4DKXBPMskXDGVXpTjzU67eABGLu42r-rJXg-NEg.PUgtW2P7s29a7NesqftLQE81-GbNcFmKPDm7SjoVWlgg.JPEG.ujinhahahaha%2F2022_K%25B8%25AE%25B1%25D7_%25BD%25C2%25B0%25AD_%25C7%25C3%25B7%25B9%25C0%25CC%25BF%25C0%25C7%25C1_1%25C2%25F7%25C0%25FC.jpg&type=sc960_832",
            "https://search.pstatic.net/common/?src=http%3A%2F%2Fblogfiles.naver.net%2FMjAyNTEwMTZfMjk5%2FMDAxNzYwNjE4Nzk3MTk1.M-xt0m0ikOZEKX9rLQzcqcsxyKlOtvqFAR3gv3LYpKsg.hnPKjzhQBqE5gT6h2hQl_Ry932uYRbnecZCijwjzazcg.PNG%2FKakaoTalk_20251016_214109831_01.png&type=sc960_832",
            "https://search.pstatic.net/common/?src=http%3A%2F%2Fimgnews.naver.net%2Fimage%2F014%2F2013%2F08%2F29%2F20130829100542486_59_20130829100606.jpg&type=sc960_832"
    };

    private static final String[] EXHIBITION_IMAGES = {
            "https://postfiles.pstatic.net/MjAyNDA2MjBfMjAz/MDAxNzE4ODgyMjg1MjM3.p0ChbxK7DMolTwxflONDQFnjvNAna3312B3bLgBIgiIg.gct1wZU45h4qONZbVV2XY7sy8su-y0G-H0FritKU7esg.PNG/%EC%82%B6%EC%9D%98%EC%98%88%EC%B0%AC.png?type=w966",
            "https://search.pstatic.net/common/?src=http%3A%2F%2Fcafefiles.naver.net%2FMjAxNzExMDZfMjM3%2FMDAxNTA5OTc3MzczODcy.JtMKcw8xQKDqNOSmfF9mgb6rBJGN-JCfGW3fRup6BAIg.dHnNYDnxYrB9Rlf3de4nFxSJ2syT-FxE5SV67YXjCqIg.JPEG.skysea36%2F7%25C8%25B8%25C6%25F7%25BD%25BA%25C5%25CD.jpg&type=sc960_832",
            "https://search.pstatic.net/common/?src=http%3A%2F%2Fimgnews.naver.net%2Fimage%2F277%2F2017%2F12%2F17%2F0004141702_001_20171217221006523.jpg&type=sc960_832"
    };

    private static final String[] FESTIVAL_IMAGES = {
            "https://search.pstatic.net/sunny/?src=https%3A%2F%2Fi.pinimg.com%2F736x%2Fb8%2F37%2F91%2Fb837919baf2eb85a8348f40ec815c178.jpg&type=sc960_832",
            "https://search.pstatic.net/common/?src=http%3A%2F%2Fblogfiles.naver.net%2FMjAxODA5MDRfNzcg%2FMDAxNTM2MDM5MDQ1ODc5.E5Cjoa8ewWNCuvajSZcElmihb6gp5EyvNo3SY4WB9rMg.Bgy8PCp_SHo4xKUt2tl5HZ0eMeAsfKHC4YmwoxWnZVcg.JPEG.sumf2018%2Fsumf%25C6%25F7%25BD%25BA%25C5%25CD_20180903-01.jpg&type=sc960_832",
            "https://search.pstatic.net/common/?src=http%3A%2F%2Fimgnews.naver.net%2Fimage%2F5575%2F2019%2F07%2F23%2F0000054607_002_20190723163101841.jpg&type=sc960_832"
    };

    private static final String[] OPERA_IMAGES = {
            "https://search.pstatic.net/common/?src=http%3A%2F%2Fblogfiles.naver.net%2FMjAyNDEwMjBfNjQg%2FMDAxNzI5NDAwNDU2NTMy.DdqiiUTQvKmzPo7ukMkvgvGQ9VmkRaViNjdBx5tigvUg.xwb1f0ARDwevlOnZ6IXimfYk3VtD73AgoQbUgrmOQ2Ag.JPEG%2F%25B4%25D9%25BF%25EE%25B7%25CE%25B5%25E5.jpg&type=sc960_832",
            "https://search.pstatic.net/common/?src=http%3A%2F%2Fblogfiles.naver.net%2FMjAyMjA0MDdfMjY0%2FMDAxNjQ5MzE0OTQyOTY5.xV5Kx4Bv8-OjrEBnqD3YRaGwPfm97FLpgTnm08EoLdYg.wt46rma-nOz7TzwqxbspmUIAxeDSbDWidYG9m6psDXsg.JPEG.koreaopentheater%2F%25C5%25F5%25B6%25F5%25B5%25B5%25C6%25AE_%25C6%25F7%25BD%25BA%25C5%25CD.jpg&type=sc960_832",
            "https://search.pstatic.net/common/?src=http%3A%2F%2Fblogfiles.naver.net%2FMjAyMDExMjBfMjk4%2FMDAxNjA1ODU1NTcyNjc1.Gp-dgSPZ5Ksun1fxWJGCHlt7L7fr0orot1M3AWr9wmMg.tVGfiKsFkoYCBrdd5oKvsH_zqBkZxd7luq8napoZ9g8g.JPEG.iluvgac%2F%25B6%25F3%25BA%25B8%25BF%25A5_%25C6%25F7%25BD%25BA%25C5%25CD_%25C3%25D6%25C1%25BE.jpg&type=sc960_832"
    };

    private static final String[] COMEDY_IMAGES = {
            "https://search.pstatic.net/common/?src=http%3A%2F%2Fimgnews.naver.net%2Fimage%2F109%2F2017%2F07%2F21%2F0003581768_001_20170721080828384.jpg&type=sc960_832",
            "https://search.pstatic.net/common/?src=http%3A%2F%2Fimgnews.naver.net%2Fimage%2F144%2F2016%2F06%2F28%2Fl_2016062802001201300301412_99_20160628172306.jpg&type=sc960_832",
            "https://search.pstatic.net/common/?src=http%3A%2F%2Fblogfiles.naver.net%2FMjAyNDA2MjlfMTc1%2FMDAxNzE5NjU3MjQyNDE2.B2ETzUQnXoJ8DiKgKLoP2k25jYlVrpiaIyGqvApeShgg.vs6M2wjoMnYLHdzSL_Bmxu-gqzCfYG2ap6hmd0ASwX4g.JPEG%2FIMG_2677.JPG&type=sc960_832"
    };

    private static final String[] KIDS_IMAGES = {
            "https://search.pstatic.net/common/?src=http%3A%2F%2Fblogfiles.naver.net%2F20150204_214%2Fxaioyuzzang_1423050343767iG1Qe_JPEG%2F1423049739411.jpeg&type=sc960_832",
            "https://search.pstatic.net/common/?src=http%3A%2F%2Fimgnews.naver.net%2Fimage%2F144%2F2019%2F05%2F05%2F0000609211_001_20190505081205997.jpg&type=sc960_832",
            "https://search.pstatic.net/common/?src=http%3A%2F%2Fblogfiles.naver.net%2FMjAxNzA4MDRfMTUy%2FMDAxNTAxODEzMzg4MDY4.6uyeySDC-RXkrOTyasg95aW0AgGDMFyMXe52-d1bcSIg.KRlj1NYCp2c_wh29Fs7P0NoAip7mHaz2-aV-YLFB-zEg.JPEG.hacu_ent%2F1.jpg&type=sc960_832"
    };

    private static final String[] THEATER_IMAGES = {
            "https://search.pstatic.net/common/?src=http%3A%2F%2Fblogfiles.naver.net%2FMjAxODAyMjhfMTkx%2FMDAxNTE5Nzc0ODUyNjg1.lNONQ_0lD2h74lRnWiE9wWypWjbEOs2sOlswVDYLu74g.aBgzt2lxEH244kxWL88eHREptP8uCNNr4qwEj-jgWlkg.JPEG.nura98%2F%25C6%25F7%25BD%25BA%25C5%25CD.jpg&type=sc960_832",
            "https://search.pstatic.net/common/?src=http%3A%2F%2Fblogfiles.naver.net%2FMjAyNDEyMDdfOTQg%2FMDAxNzMzNTM5NzA1NzI2.LUTf3FG2w5d-0-Yums2sJVwTyYcRuRp0O42_svF0S48g.SsfCKYFpt0GUGGCZIwXbaawZmFwxgfnKh_DrO2xIdcUg.JPEG%2F%25B8%25DE%25C0%25CE-%25C6%25F7%25BD%25BA%25C5%25CD.jpg&type=sc960_832",
            "https://search.pstatic.net/common/?src=http%3A%2F%2Fimgnews.naver.net%2Fimage%2F5339%2F2020%2F04%2F22%2F0000210411_001_20200422134231556.jpg&type=sc960_832"
    };

    private static final String[] DEFAULT_IMAGES = {
            "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?w=800"
    };

    public InitProgress generateEvents(DataInitRequest request, InitProgress progress) {

        if (request.getClearExisting()) {
            eventRepository.deleteAll();
        }

        int threadCount = request.getThreadCount();
        int eventsPerThread = request.getCount() / threadCount;

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        try {
            for (int threadId = 0; threadId < threadCount; threadId++) {
                final int id = threadId;
                executorService.submit(() -> {
                    try {
                        insertEvents(id, eventsPerThread, request.getBatchSize(), progress);
                    } catch (Exception e) {
                        log.error("스레드 {} 오류", id, e);
                        progress.incrementError(eventsPerThread);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            progress.complete();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            progress.fail();
            log.error("이벤트 생성 중단됨", e);
        } finally {
            executorService.shutdown();
        }

        return progress;
    }

    @Transactional
    public void insertEvents(int threadId, int totalCount, int batchSize, InitProgress progress) {
        int batchCount = totalCount / batchSize;

        for (int batch = 0; batch < batchCount; batch++) {
            Instant batchStart = Instant.now();

            List<Event> events = new ArrayList<>(batchSize);
            for (int i = 0; i < batchSize; i++) {
                Category category = getRandomCategory();

                Event event = Event.builder()
                        .id(snowflake.nextId())
                        .title(getRandomTitle(category) + " #" + (threadId * totalCount + batch * batchSize + i))
                        .description("테스트 이벤트 설명")
                        .category(category)
                        .venue(VENUES[random.nextInt(VENUES.length)])
                        .eventDate(LocalDateTime.now().plusDays(random.nextInt(365)))
                        .imageUrl(getRandomImageUrl(category))
                        .build();

                events.add(event);
            }

            try {
                eventRepository.saveAll(events);
                progress.incrementCompleted(batchSize);
                progress.addTime(java.time.Duration.between(batchStart, Instant.now()).toMillis());
            } catch (Exception e) {
                log.error("배치 저장 실패", e);
                progress.incrementError(batchSize);
            }
        }
    }

    private Category getRandomCategory() {
        Category[] categories = Category.values();
        return categories[random.nextInt(categories.length)];
    }

    private String getRandomTitle(Category category) {
        return switch (category) {
            case CONCERT -> CONCERT_TITLES[random.nextInt(CONCERT_TITLES.length)];
            case MUSICAL -> MUSICAL_TITLES[random.nextInt(MUSICAL_TITLES.length)];
            case SPORTS -> SPORT_TITLES[random.nextInt(SPORT_TITLES.length)];
            case EXHIBITION -> EXHIBITION_TITLES[random.nextInt(EXHIBITION_TITLES.length)];
            case FESTIVAL -> FESTIVAL_TITLES[random.nextInt(FESTIVAL_TITLES.length)];
            case OPERA -> OPERA_TITLES[random.nextInt(OPERA_TITLES.length)];
            case COMEDY -> COMEDY_TITLES[random.nextInt(COMEDY_TITLES.length)];
            case KIDS -> KIDS_TITLES[random.nextInt(KIDS_TITLES.length)];
            case THEATER -> THEATER_TITLES[random.nextInt(THEATER_TITLES.length)];
            default -> "특별 이벤트";
        };
    }

    private String getRandomImageUrl(Category category) {
        return switch (category) {
            case CONCERT -> CONCERT_IMAGES[random.nextInt(CONCERT_IMAGES.length)];
            case MUSICAL -> MUSICAL_IMAGES[random.nextInt(MUSICAL_IMAGES.length)];
            case SPORTS -> SPORTS_IMAGES[random.nextInt(SPORTS_IMAGES.length)];
            case EXHIBITION -> EXHIBITION_IMAGES[random.nextInt(EXHIBITION_IMAGES.length)];
            case FESTIVAL -> FESTIVAL_IMAGES[random.nextInt(FESTIVAL_IMAGES.length)];
            case OPERA -> OPERA_IMAGES[random.nextInt(OPERA_IMAGES.length)];
            case COMEDY -> COMEDY_IMAGES[random.nextInt(COMEDY_IMAGES.length)];
            case KIDS -> KIDS_IMAGES[random.nextInt(KIDS_IMAGES.length)];
            case THEATER -> THEATER_IMAGES[random.nextInt(THEATER_IMAGES.length)];
            default -> DEFAULT_IMAGES[random.nextInt(DEFAULT_IMAGES.length)];
        };
    }
}
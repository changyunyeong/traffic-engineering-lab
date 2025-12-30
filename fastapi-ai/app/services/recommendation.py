import pandas as pd
import httpx
from typing import List, Dict
from pathlib import Path
import logging
from datetime import datetime

from app.config import get_settings
from app.models.ml_models import RecommendationModel
from app.models.schemas import EventRecommendation

settings = get_settings()
logger = logging.getLogger(__name__)


class RecommendationService:
    def __init__(self):
        self.model: RecommendationModel = None
        self.model_path = Path(settings.RECOMMENDATION_MODEL_PATH)
        self._load_or_init_model()
    
    def _load_or_init_model(self):
        """모델 로드 또는 초기화"""
        if self.model_path.exists():
            try:
                self.model = RecommendationModel.load(str(self.model_path))
                logger.info("기존 추천 모델 로드 완료")
            except Exception as e:
                logger.error(f"모델 로드 실패: {e}")
                self.model = RecommendationModel()
        else:
            self.model = RecommendationModel()
            logger.info("새로운 추천 모델 초기화")
    
    async def fetch_training_data(self) -> pd.DataFrame:
        """Spring Boot API에서 학습 데이터 가져오기"""
        async with httpx.AsyncClient(timeout=30.0) as client:
            try:
                # 모든 예약 데이터 가져오기
                response = await client.get(
                    f"{settings.SPRINGBOOT_API_URL}/api/v1/reservations/all"
                )
                response.raise_for_status()
                reservations = response.json()['data']
                
                # user_id, event_id, interaction_score 형식으로 변환
                interactions = []
                for reservation in reservations:
                    interactions.append({
                        'user_id': reservation['userId'],
                        'event_id': reservation['ticket']['eventId'],
                        'interaction_score': 1.0  # 구매 = 1점
                    })
                
                df = pd.DataFrame(interactions)
                
                # 같은 사용자가 같은 이벤트 여러 번 구매 시 점수 합산
                df = df.groupby(['user_id', 'event_id'])['interaction_score'].sum().reset_index()
                
                logger.info(f"학습 데이터 수집 완료: {len(df)}개 상호작용")
                return df
                
            except Exception as e:
                logger.error(f"학습 데이터 수집 실패: {e}")
                # Mock 데이터 반환
                return self._generate_mock_data()
    
    def _generate_mock_data(self) -> pd.DataFrame:
        """Mock 데이터 생성 (개발/테스트용)"""
        import numpy as np
        
        n_users = 100
        n_events = 20
        n_interactions = 500
        
        data = {
            'user_id': np.random.randint(1, n_users + 1, n_interactions),
            'event_id': np.random.randint(1, n_events + 1, n_interactions),
            'interaction_score': np.random.choice([1.0, 2.0, 3.0], n_interactions)
        }
        
        df = pd.DataFrame(data)
        df = df.groupby(['user_id', 'event_id'])['interaction_score'].sum().reset_index()
        
        logger.info(f"Mock 데이터 생성: {len(df)}개 상호작용")
        return df
    
    async def train_model(self, force_retrain: bool = False):
        """모델 학습"""
        if self.model.is_fitted and not force_retrain:
            logger.info("이미 학습된 모델 존재, 재학습 스킵")
            return
        
        # 학습 데이터 수집
        training_data = await self.fetch_training_data()
        
        if len(training_data) < 10:
            logger.warning("학습 데이터 부족, 모델 학습 스킵")
            return
        
        # 모델 학습
        self.model.fit(training_data)
        
        # 모델 저장
        self.model_path.parent.mkdir(parents=True, exist_ok=True)
        self.model.save(str(self.model_path))
        
        logger.info("추천 모델 학습 및 저장 완료")
    
    async def get_recommendations(
        self,
        user_id: int,
        limit: int = 10
    ) -> List[EventRecommendation]:
        """사용자에게 이벤트 추천"""
        if not self.model.is_fitted:
            logger.warning("모델이 학습되지 않아 자동 학습 시도")
            await self.train_model()
        
        # 추천 받기
        recommendations = self.model.recommend(user_id, limit)
        
        # 이벤트 정보 조회 (Spring Boot API)
        event_details = await self._fetch_event_details([event_id for event_id, _ in recommendations])
        
        # 응답 생성
        results = []
        for event_id, score in recommendations:
            event = event_details.get(event_id, {})
            results.append(EventRecommendation(
                event_id=event_id,
                title=event.get('title', f'이벤트 {event_id}'),
                score=float(score),
                reason=self._generate_reason(score)
            ))
        
        return results
    
    async def _fetch_event_details(self, event_ids: List[int]) -> Dict[int, dict]:
        """이벤트 상세 정보 조회"""
        async with httpx.AsyncClient(timeout=10.0) as client:
            event_map = {}
            for event_id in event_ids:
                try:
                    response = await client.get(
                        f"{settings.SPRINGBOOT_API_URL}/api/v1/events/{event_id}"
                    )
                    if response.status_code == 200:
                        event_map[event_id] = response.json()['data']
                except Exception as e:
                    logger.warning(f"이벤트 {event_id} 조회 실패: {e}")
            
            return event_map
    
    def _generate_reason(self, score: float) -> str:
        """추천 이유 생성"""
        if score >= 0.8:
            return "비슷한 취향의 사용자들이 매우 선호하는 이벤트입니다"
        elif score >= 0.6:
            return "당신과 유사한 사용자들이 좋아하는 이벤트입니다"
        elif score >= 0.4:
            return "관심 있을 만한 이벤트입니다"
        else:
            return "새로운 카테고리의 이벤트를 경험해보세요"


# 싱글톤 인스턴스
_recommendation_service = None

def get_recommendation_service() -> RecommendationService:
    global _recommendation_service
    if _recommendation_service is None:
        _recommendation_service = RecommendationService()
    return _recommendation_service
import pandas as pd
import httpx
from typing import List
from pathlib import Path
import logging
from datetime import datetime

from app.config import get_settings
from app.models.ml_models import AnomalyDetectionModel
from app.models.schemas import ReservationData, AnomalyResult

settings = get_settings()
logger = logging.getLogger(__name__)


class AnomalyDetectionService:
    def __init__(self):
        self.model: AnomalyDetectionModel = None
        self.model_path = Path(settings.ANOMALY_MODEL_PATH)
        self._load_or_init_model()
    
    def _load_or_init_model(self):
        """모델 로드 또는 초기화"""
        if self.model_path.exists():
            try:
                self.model = AnomalyDetectionModel.load(str(self.model_path))
                logger.info("기존 이상 탐지 모델 로드 완료")
            except FileNotFoundError as e:
                logger.error(f"모델 파일을 찾을 수 없음: {self.model_path}", exc_info=True)
                self.model = AnomalyDetectionModel()
            except Exception as e:
                logger.error(f"모델 로드 중 오류 발생: {e}", exc_info=True) 
                self.model = AnomalyDetectionModel()
        else:
            self.model = AnomalyDetectionModel()
            logger.info("새로운 이상 탐지 모델 초기화")
    
    async def fetch_training_data(self) -> pd.DataFrame:
        """Spring Boot API에서 학습 데이터 가져오기"""
        async with httpx.AsyncClient(timeout=30.0) as client:
            try:
                response = await client.get(
                    f"{settings.SPRINGBOOT_API_URL}/api/v1/reservations/all"
                )
                response.raise_for_status()
                reservations = response.json()['data']
                
                # DataFrame으로 변환
                data = []
                for r in reservations:
                    data.append({
                        'user_id': r['userId'],
                        'event_id': r['ticket']['eventId'],
                        'ticket_id': r['ticketId'],
                        # 'price': r['price'],
                        # 'purchase_time': r['createdAt'],
                        'ip_address': r.get('ipAddress', 'unknown'),
                        'user_agent': r.get('userAgent', 'unknown')
                    })
                
                df = pd.DataFrame(data)
                logger.info(f"학습 데이터 수집 완료: {len(df)}건")
                return df
                
            except Exception as e:
                logger.error(f"학습 데이터 수집 실패: {e}")
                return self._generate_mock_data()
            except httpx.RequestError as e:
                logger.error(f"학습 데이터 API 연결 실패: {e}", exc_info=True)
                return self._generate_mock_data()
            except Exception as e:
                logger.error(f"학습 데이터 수집 중 예상치 못한 오류: {e}", exc_info=True)
                return self._generate_mock_data()
    
    def _generate_mock_data(self) -> pd.DataFrame:
        """Mock 데이터 생성"""
        import numpy as np
        from datetime import timedelta
        
        n_samples = 1000
        base_time = datetime.now()
        
        data = {
            'user_id': np.random.randint(1, 1001, n_samples),
            'event_id': np.random.randint(1, 51, n_samples),
            'ticket_id': np.random.randint(1, 201, n_samples),
            # 'price': np.random.randint(50000, 200000, n_samples),
            # 'purchase_time': [
            #     (base_time - timedelta(days=np.random.randint(0, 90))).isoformat()
            #     for _ in range(n_samples)
            # ],
            'ip_address': [f"192.168.1.{np.random.randint(1, 255)}" for _ in range(n_samples)]
        }
        
        df = pd.DataFrame(data)
        logger.info(f"Mock 데이터 생성: {len(df)}건")
        return df
    
    async def train_model(self, force_retrain: bool = False):
        """모델 학습"""
        if self.model.is_fitted and not force_retrain:
            logger.info("이미 학습된 모델 존재")
            return
        
        training_data = await self.fetch_training_data()
        
        if len(training_data) < 100:
            logger.warning("학습 데이터 부족")
            return
        
        self.model.fit(training_data)
        
        self.model_path.parent.mkdir(parents=True, exist_ok=True)
        if not self.model.is_fitted:
            logger.warning("모델 미학습, 자동 학습 시도")
            try:
                await self.train_model()
                if not self.model.is_fitted:
                    raise ValueError("모델 학습 실패: 학습 데이터가 부족합니다")
            except Exception as e:
                logger.error(f"자동 모델 학습 실패: {e}", exc_info=True)
                raise ValueError(f"모델을 학습할 수 없습니다: {str(e)}")
    

    async def detect_anomalies(
        self,
        reservations: List[ReservationData]
    ) -> List[AnomalyResult]:
        """이상 거래 탐지"""
        if not self.model.is_fitted:
            logger.warning("모델 미학습, 자동 학습 시도")
            await self.train_model()
        
        # 예약 데이터를 DataFrame으로 변환
        data = []
        for r in reservations:
            data.append({
                # 'reservation_id': r.reservation_id,
                'user_id': r.user_id,
                'event_id': r.event_id,
                'ticket_id': r.ticket_id,
                # 'price': r.price,
                # 'purchase_time': r.purchase_time.isoformat(),
                'ip_address': r.ip_address or 'unknown',
                'user_agent': r.user_agent or 'unknown'
            })
        
        df = pd.DataFrame(data)
        
        # 이상치 탐지
        predictions, scores = self.model.predict(df)
        
        # 결과 생성
        results = []
        for i, (pred, score) in enumerate(zip(predictions, scores)):
            is_anomaly = pred == -1
            
            # 점수를 -1~1 범위로 정규화
            normalized_score = float(score)
            
            # 리스크 레벨 결정
            if score < -0.5:
                risk_level = "HIGH"
            elif score < -0.2:
                risk_level = "MEDIUM"
            else:
                risk_level = "LOW"
            
            # 이상 이유 생성
            reasons = self._generate_anomaly_reasons(df.iloc[i], is_anomaly, score)
            
            results.append(AnomalyResult(
                user_id=int(df.iloc[i]['user_id']),
                is_anomaly=bool(is_anomaly),
                anomaly_score=normalized_score,
                risk_level=risk_level,
                reasons=reasons
            ))
        
        return results
    
    def _generate_anomaly_reasons(self, row: pd.Series, is_anomaly: bool, score: float) -> List[str]:
        """이상 이유 생성"""
        if not is_anomaly:
            return ["정상 거래 패턴"]
        
        reasons = []
        
        # # 시간대 체크
        # hour = pd.to_datetime(row['purchase_time']).hour
        # if 0 <= hour <= 5:
        #     reasons.append("비정상적인 시간대 예약 (심야)")
        
        # 가격 체크
        # if row['price'] > 500000:
        #     reasons.append("비정상적으로 높은 가격")
        
        # IP 중복 체크
        if 'ip_reservation_count' in row and row.get('ip_reservation_count', 0) > 10:
            reasons.append("동일 IP에서 다수 예약")
        
        # 사용자 예약 빈도
        if 'user_reservation_count' in row and row.get('user_reservation_count', 0) > 20:
            reasons.append("단일 사용자의 과도한 예약")
        
        # 이상치 점수가 매우 낮은 경우
        if score < -0.6:
            reasons.append("전반적인 거래 패턴 이상")
        
        return reasons if reasons else ["알 수 없는 이상 패턴"]


# 싱글톤 인스턴스
_anomaly_service = None

def get_anomaly_service() -> AnomalyDetectionService:
    global _anomaly_service
    if _anomaly_service is None:
        _anomaly_service = AnomalyDetectionService()
    return _anomaly_service
from pydantic import BaseModel, Field
from typing import List, Optional
from datetime import datetime

# ==================== 공통 응답 ====================
class APIResponse(BaseModel):
    success: bool
    message: str
    data: Optional[dict] = None


# ==================== 추천 시스템 ====================
class RecommendationRequest(BaseModel):
    user_id: int = Field(..., gt=0, description="사용자 ID")
    limit: int = Field(10, ge=1, le=50, description="추천 개수")


class EventRecommendation(BaseModel):
    event_id: int
    title: str
    score: float = Field(..., ge=0, le=1, description="추천 점수")
    reason: str = Field(..., description="추천 이유")


class RecommendationResponse(BaseModel):
    user_id: int
    recommendations: List[EventRecommendation]
    generated_at: datetime


# ==================== 이상 탐지 ====================
class ReservationData(BaseModel):
    user_id: int
    event_id: int
    ticket_id: int
    # price: float
    # purchase_time: datetime
    ip_address: Optional[str] = None
    user_agent: Optional[str] = None


class AnomalyDetectionRequest(BaseModel):
    reservations: List[ReservationData]


class AnomalyResult(BaseModel):
    reservation_id: Optional[int] = None
    user_id: int
    is_anomaly: bool
    anomaly_score: float = Field(..., ge=-1, le=1, description="-1(정상) ~ 1(이상)")
    risk_level: str = Field(..., description="LOW, MEDIUM, HIGH")
    reasons: List[str] = Field(default_factory=list)


class AnomalyDetectionResponse(BaseModel):
    total_checked: int
    anomalies_found: int
    results: List[AnomalyResult]
    # checked_at: datetime


# ==================== 학습 데이터 ====================
class TrainingRequest(BaseModel):
    model_type: str = Field(..., description="recommendation 또는 anomaly")
    force_retrain: bool = Field(False, description="기존 모델 무시하고 재학습")


class TrainingResponse(BaseModel):
    model_type: str
    status: str
    metrics: dict
    trained_at: datetime


'''
Field(...) = not null
Field(10) = default value 10 

gt (Greater Than): ~보다 큼 (초과)
gt=0: 값이 0보다 커야 함 (value > 0)

ge (Greater than or Equal): ~보다 크거나 같음 (이상)
ge=1: 값이 1보다 크거나 같아야 함 (value >= 1)

le (Less than or Equal): ~보다 작거나 같음 (이하)
le=50: 값이 50보다 작거나 같아야 함 (value <= 50)
'''
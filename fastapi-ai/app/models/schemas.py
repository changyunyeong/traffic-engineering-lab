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
    

# ==================== RAG ====================
class SearchRequest(BaseModel):
    query: str = Field(..., min_length=1, max_length=500, description="검색 쿼리")
    top_k: int = Field(10, ge=1, le=50, description="반환할 결과 수")
    category: Optional[str] = Field(None, description="카테고리 필터 (CONCERT, MUSICAL, SPORTS, ETC)")

    class Config:
        json_schema_extra = {
            "example": {
                "query": "신나는 록 콘서트",
                "top_k": 5,
                "category": "CONCERT"
            }
        }


class SearchResult(BaseModel):
    event_id: int
    title: str
    category: str
    venue: str
    event_date: str
    similarity_score: float = Field(..., ge=0, le=1)
    rank: int


class SearchResponse(BaseModel):
    query: str
    total_results: int
    results: List[SearchResult]
    search_time_ms: float
    searched_at: datetime


class IndexResponse(BaseModel):
    indexed: int
    total_events: int
    message: str
    indexed_at: datetime


class SimilarEventRequest(BaseModel):
    event_id: int = Field(..., gt=0, description="기준 이벤트 ID")
    top_k: int = Field(5, ge=1, le=20, description="반환할 결과 수")


# ==================== VLM ====================
class MoodScore(BaseModel):
    label: str
    score: float = Field(..., ge=0, le=1)


class ImageAnalysisResponse(BaseModel):
    moods: List[MoodScore] = Field(..., description="분위기 분석 결과")
    categories: List[MoodScore] = Field(..., description="카테고리 분류 결과")
    audiences: List[MoodScore] = Field(..., description="타겟 관객 분석")
    description: str = Field(..., description="이미지 설명")
    analyzed_at: datetime


class ImageSearchResult(BaseModel):
    event_id: int
    title: Optional[str] = None
    category: Optional[str] = None
    similarity_score: float = Field(..., ge=-1, le=1)
    analysis: Optional[dict] = None


class ImageSearchResponse(BaseModel):
    total_results: int
    results: List[ImageSearchResult]
    searched_at: datetime


class RegisterImageResponse(BaseModel):
    event_id: int
    analysis: dict
    image_path: str
    registered_at: datetime


class TextSearchRequest(BaseModel):
    query: str = Field(..., min_length=1, max_length=200, description="검색 텍스트")
    top_k: int = Field(5, ge=1, le=20, description="반환할 결과 수")

    class Config:
        json_schema_extra = {
            "example": {
                "query": "화려한 콘서트 포스터",
                "top_k": 5
            }
        }


class ManualRegisterRequest(BaseModel):
    event_id: int = Field(..., gt=0, description="이벤트 ID")
    category: str = Field(..., description="카테고리 (스포츠, 콘서트, 뮤지컬 등)")
    mood: str = Field(..., description="분위기 (열정적인, 신나는, 감동적인 등)")
    audience: str = Field(..., description="타겟 관객 (가족, 연인, 친구, 팬덤 등)")
    title: Optional[str] = Field(None, description="이벤트 제목")
    description: Optional[str] = Field(None, description="이벤트 설명")
    image_url: Optional[str] = Field(None, description="이미지 URL (선택)")

    class Config:
        json_schema_extra = {
            "example": {
                "event_id": 1,
                "category": "스포츠",
                "mood": "열정적인",
                "audience": "팬덤",
                "title": "삼성 라이온즈 vs LG 트윈스",
                "description": "KBO 리그 정규시즌"
            }
        }


class BulkRegisterRequest(BaseModel):
    events: List[ManualRegisterRequest] = Field(..., description="등록할 이벤트 목록")

    class Config:
        json_schema_extra = {
            "example": {
                "events": [
                    {"event_id": 1, "category": "스포츠", "mood": "열정적인", "audience": "팬덤", "title": "삼성 vs LG"},
                    {"event_id": 2, "category": "콘서트", "mood": "신나는", "audience": "팬덤", "title": "BTS 콘서트"},
                    {"event_id": 3, "category": "뮤지컬", "mood": "감동적인", "audience": "연인", "title": "레미제라블"}
                ]
            }
        }


class ManualRegisterResponse(BaseModel):
    event_id: int
    category: str
    mood: str
    audience: str
    registered_at: datetime

# ==================== AI Model Responses ====================
class StatsResponse(BaseModel):
    status: str
    collection_name: Optional[str] = None
    document_count: int = 0
    embedding_model: Optional[str] = None

    
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
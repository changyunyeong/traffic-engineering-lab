from fastapi import APIRouter, Depends, HTTPException
from datetime import datetime
import logging

from app.models.schemas import (
    RecommendationRequest,
    RecommendationResponse,
    APIResponse
)
from app.services.recommendation import get_recommendation_service, RecommendationService

router = APIRouter(prefix="/recommendations", tags=["Recommendations"])
logger = logging.getLogger(__name__)


@router.post("", response_model=RecommendationResponse)
async def get_recommendations(
    request: RecommendationRequest,
    # service 변수의 타입은 RecommendationService 클래스 (타입 힌트)
    # 실제로 주입되는 값은 get_recommendation_service 함수가 반환하는 객체 (의존성 주입)
    service: RecommendationService = Depends(get_recommendation_service)
):
    """
    사용자에게 이벤트 추천
    
    - user_id: 사용자 ID
    - limit: 추천 개수 (최대 50)
    """
    try:
        recommendations = await service.get_recommendations(
            user_id=request.user_id,
            limit=request.limit
        )
        
        return RecommendationResponse(
            user_id=request.user_id,
            recommendations=recommendations,
            generated_at=datetime.now()
        )
    
    except Exception as e:
        logger.error(f"추천 생성 실패: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/train", response_model=APIResponse)
async def train_recommendation_model(
    force_retrain: bool = False, # true일 경우 기존 모델 무시하고 재학습
    service: RecommendationService = Depends(get_recommendation_service)
):

    try:
        await service.train_model(force_retrain=force_retrain)
        
        return APIResponse(
            success=True,
            message="모델 학습 완료",
            data={"model_fitted": service.model.is_fitted}
        )
    
    except Exception as e:
        logger.error(f"모델 학습 실패: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/health", response_model=APIResponse)
async def health_check(
    service: RecommendationService = Depends(get_recommendation_service)
):
    """추천 서비스 상태 확인"""
    return APIResponse(
        success=True,
        message="추천 서비스 정상",
        data={
            "model_loaded": service.model is not None,
            "model_fitted": service.model.is_fitted if service.model else False
        }
    )
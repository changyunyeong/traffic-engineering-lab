from fastapi import APIRouter, Depends, HTTPException
from datetime import datetime
import logging

from app.models.schemas import (
    AnomalyDetectionRequest,
    AnomalyDetectionResponse,
    AnomalyResult,
    APIResponse
)
from app.services.anomaly_detection import get_anomaly_service, AnomalyDetectionService

router = APIRouter(prefix="/anomaly", tags=["Anomaly Detection"])
logger = logging.getLogger(__name__)


@router.post("/detect", response_model=AnomalyDetectionResponse)
async def detect_anomalies(
    request: AnomalyDetectionRequest,
    service: AnomalyDetectionService = Depends(get_anomaly_service)
):
    """
    예약 데이터에서 이상 거래 탐지
    """
    try:
        results = await service.detect_anomalies(request.reservations)
        
        anomalies_found = sum(1 for r in results if r.is_anomaly)
        
        return AnomalyDetectionResponse(
            total_checked=len(results),
            anomalies_found=anomalies_found,
            results=results,
            # checked_at=datetime.now()
        )
    
    except ValueError as e:
        logger.error(f"이상 탐지 검증 실패: {e}", exc_info=True)
        raise HTTPException(status_code=400, detail=f"잘못된 요청 데이터: {str(e)}")
    except KeyError as e:
        logger.error(f"이상 탐지 필드 누락: {e}", exc_info=True)
        raise HTTPException(status_code=400, detail=f"필수 필드 누락: {str(e)}")
    except Exception as e:
        logger.error(f"이상 탐지 실패: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"서버 내부 오류: {str(e)}")



@router.post("/train", response_model=APIResponse)
async def train_anomaly_model(
    force_retrain: bool = False,
    service: AnomalyDetectionService = Depends(get_anomaly_service)
):
    """
    이상 탐지 모델 학습
    """
    try:
        await service.train_model(force_retrain=force_retrain)
        
        return APIResponse(
            success=True,
            message="이상 탐지 모델 학습 완료",
            data={"model_fitted": service.model.is_fitted}
        )
    
    except Exception as e:
        logger.error(f"모델 학습 실패: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/health", response_model=APIResponse)
async def health_check(
    service: AnomalyDetectionService = Depends(get_anomaly_service)
):
    """이상 탐지 서비스 상태 확인"""
    return APIResponse(
        success=True,
        message="이상 탐지 서비스 정상",
        data={
            "model_loaded": service.model is not None,
            "model_fitted": service.model.is_fitted if service.model else False
        }
    )
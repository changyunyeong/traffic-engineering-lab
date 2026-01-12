from fastapi import APIRouter, Depends, HTTPException, Query, File, UploadFile
from pydantic import BaseModel, Field
from typing import List, Optional
from datetime import datetime
from io import BytesIO
from PIL import Image
import logging

from app.models.schemas import (
    MoodScore,
    ImageAnalysisResponse,
    ImageSearchResult,
    ImageSearchResponse,
    RegisterImageResponse,
    TextSearchRequest,
    StatsResponse,
    ManualRegisterRequest,
    ManualRegisterResponse,
    BulkRegisterRequest,
    APIResponse
)
from app.services.vlm_service import get_vlm_service, VLMService
from app.config import get_settings

router = APIRouter(prefix="/vlm", tags=["VLM - Visual Language Model"])
settings = get_settings()
logger = logging.getLogger(__name__)


@router.post("/register/manual", response_model=ManualRegisterResponse)
async def register_manual(
        request: ManualRegisterRequest,
        service: VLMService = Depends(get_vlm_service)
):
    """
    이벤트 메타데이터 수동 등록 (텍스트 기반 임베딩 생성)
    
    CLIP 자동 분석 대신 직접 분류 정보를 입력합니다.
    입력된 텍스트로 임베딩을 생성하여 검색 기능도 지원합니다.
    """
    try:
        # 텍스트 임베딩 생성용 문자열 조합
        embedding_text = f"{request.category} {request.mood} {request.audience}"
        if request.title:
            embedding_text += f" {request.title}"
        if request.description:
            embedding_text += f" {request.description}"

        # 텍스트 임베딩 생성
        text_embedding = await service.encode_text(embedding_text)

        # 메타데이터 구성
        metadata = {
            "event_id": request.event_id,
            "analysis": {
                "categories": [{"label": request.category, "score": 1.0}],
                "moods": [{"label": request.mood, "score": 1.0}],
                "audiences": [{"label": request.audience, "score": 1.0}],
                "description": request.description or f"{request.mood} 분위기의 {request.category} 이벤트입니다.",
                "confidence": "수동등록"
            },
            "title": request.title,
            "embedding_text": embedding_text,
            "manual_registered": True
        }

        # 서비스에 등록 (메모리 + ChromaDB)
        await service.register_manual(request.event_id, text_embedding, metadata)

        logger.info(f"이벤트 {request.event_id} 수동 등록 완료: {request.category}")

        return ManualRegisterResponse(
            event_id=request.event_id,
            category=request.category,
            mood=request.mood,
            audience=request.audience,
            registered_at=datetime.now()
        )

    except Exception as e:
        logger.error(f"수동 등록 실패: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/register/bulk")
async def register_bulk(
        request: BulkRegisterRequest,
        service: VLMService = Depends(get_vlm_service)
):
    """
    여러 이벤트 일괄 수동 등록 (텍스트 기반 임베딩 생성)
    
    더미데이터를 한 번에 등록할 때 사용합니다.
    각 이벤트마다 텍스트 임베딩이 생성되어 검색 기능도 지원합니다.
    """
    try:
        registered = []

        for event in request.events:
            # 텍스트 임베딩 생성용 문자열 조합
            embedding_text = f"{event.category} {event.mood} {event.audience}"
            if event.title:
                embedding_text += f" {event.title}"
            if event.description:
                embedding_text += f" {event.description}"

            # 텍스트 임베딩 생성
            text_embedding = await service.encode_text(embedding_text)

            # 메타데이터 구성
            metadata = {
                "event_id": event.event_id,
                "analysis": {
                    "categories": [{"label": event.category, "score": 1.0}],
                    "moods": [{"label": event.mood, "score": 1.0}],
                    "audiences": [{"label": event.audience, "score": 1.0}],
                    "description": event.description or f"{event.mood} 분위기의 {event.category} 이벤트입니다.",
                    "confidence": "수동등록"
                },
                "title": event.title,
                "embedding_text": embedding_text,
                "manual_registered": True
            }

            # 서비스에 등록 (메모리 + ChromaDB)
            await service.register_manual(event.event_id, text_embedding, metadata)
            registered.append(event.event_id)

        logger.info(f"일괄 등록 완료: {len(registered)}건")

        return {
            "success": True,
            "registered_count": len(registered),
            "event_ids": registered,
            "registered_at": datetime.now()
        }

    except Exception as e:
        logger.error(f"일괄 등록 실패: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))
    

@router.post("/analyze", response_model=ImageAnalysisResponse)
async def analyze_image(
    file: UploadFile = File(..., description="분석할 이미지 파일"),
    service: VLMService = Depends(get_vlm_service)
):
    try:
        image = await validate_and_load_image(file)
        analysis = await service.analyze_image(image)

        return ImageAnalysisResponse(
            moods=[MoodScore(**m) for m in analysis.get("moods", [])],
            categories=[MoodScore(**c) for c in analysis.get("categories", [])],
            audiences=[MoodScore(**a) for a in analysis.get("audiences", [])],
            description=analysis.get("description", ""),
            analyzed_at=datetime.now()
        )
    
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"이미지 분석 실패: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"이미지 분석 중 오류: {str(e)}")
    

@router.post("/register/{event_id}", response_model=RegisterImageResponse)
async def register_event_image(
        event_id: int,
        file: UploadFile = File(..., description="이벤트 포스터 이미지"),
        service: VLMService = Depends(get_vlm_service)
):
    try:
        image = await validate_and_load_image(file)
        result = await service.register_event_image(
            event_id=event_id,
            image=image,
            metadata={"filename": file.filename}
        )

        return RegisterImageResponse(
            event_id=event_id,
            analysis=result.get("analysis", {}),
            image_path=result.get("image_path", ""),
            registered_at=datetime.now()
        )

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"이미지 등록 실패: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/search/image", response_model=ImageSearchResponse)
async def search_by_image(
        file: UploadFile = File(..., description="검색할 이미지"),
        top_k: int = Query(5, ge=1, le=20, description="반환할 결과 수"),
        service: VLMService = Depends(get_vlm_service)
):
    try:
        image = await validate_and_load_image(file)
        results = await service.search_by_image(image, top_k=top_k)

        return ImageSearchResponse(
            total_results=len(results),
            results=[ImageSearchResult(
                event_id=r["event_id"],
                title=r.get("metadata", {}).get("title"),
                category=r.get("metadata", {}).get("analysis", {}).get("categories", [{}])[0].get("label"),
                similarity_score=r["similarity_score"],
                analysis=r.get("metadata", {}).get("analysis")
            ) for r in results],
            searched_at=datetime.now()
        )

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"이미지 검색 실패: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))
    

@router.post("/search/text", response_model=ImageSearchResponse)
async def search_by_text(
        request: TextSearchRequest,
        service: VLMService = Depends(get_vlm_service)
):
    try:
        results = await service.search_by_text(
            query=request.query,
            top_k=request.top_k
        )

        return ImageSearchResponse(
            total_results=len(results),
            results=[ImageSearchResult(
                event_id=r["event_id"],
                title=r.get("metadata", {}).get("title"),
                category=r.get("metadata", {}).get("analysis", {}).get("categories", [{}])[0].get("label"),
                similarity_score=r["similarity_score"],
                analysis=r.get("metadata", {}).get("analysis")
            ) for r in results],
            searched_at=datetime.now()
        )

    except Exception as e:
        logger.error(f"텍스트 검색 실패: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))
    

@router.get("/search/text")
async def search_by_text_get(
        query: str = Query(..., min_length=1, max_length=200, description="검색 텍스트"),
        top_k: int = Query(5, ge=1, le=20, description="반환할 결과 수"),
        service: VLMService = Depends(get_vlm_service)
):
    try:
        results = await service.search_by_text(query=query, top_k=top_k)

        return ImageSearchResponse(
            total_results=len(results),
            results=[ImageSearchResult(
                event_id=r["event_id"],
                title=r.get("metadata", {}).get("title"),
                category=r.get("metadata", {}).get("analysis", {}).get("categories", [{}])[0].get("label"),
                similarity_score=r["similarity_score"],
                analysis=r.get("metadata", {}).get("analysis")
            ) for r in results],
            searched_at=datetime.now()
        )

    except Exception as e:
        logger.error(f"텍스트 검색 실패: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/similar/{event_id}", response_model=ImageSearchResponse)
async def get_similar_events(
        event_id: int,
        top_k: int = Query(5, ge=1, le=20, description="반환할 결과 수"),
        service: VLMService = Depends(get_vlm_service)
):
    try:
        results = await service.get_similar_events(event_id=event_id, top_k=top_k)

        if not results:
            raise HTTPException(
                status_code=404,
                detail=f"이벤트 {event_id}의 이미지가 등록되지 않았습니다"
            )

        return ImageSearchResponse(
            total_results=len(results),
            results=[ImageSearchResult(
                event_id=r["event_id"],
                title=r.get("metadata", {}).get("title"),
                category=r.get("metadata", {}).get("analysis", {}).get("categories", [{}])[0].get("label"),
                similarity_score=r["similarity_score"],
                analysis=r.get("metadata", {}).get("analysis")
            ) for r in results],
            searched_at=datetime.now()
        )

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"유사 이벤트 검색 실패: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/stats", response_model=StatsResponse)
async def get_stats(
        service: VLMService = Depends(get_vlm_service)
):
    stats = service.get_stats()
    return StatsResponse(**stats)


@router.get("/health")
async def health_check(
        service: VLMService = Depends(get_vlm_service)
):
    stats = service.get_stats()
    return {
        "status": "healthy" if stats.get("status") == "initialized" else "initializing",
        "service": "VLM",
        "details": stats
    }


@router.get("/registered")
async def get_registered_events(
        service: VLMService = Depends(get_vlm_service)
):
    """
    등록된 모든 이벤트 메타데이터 조회
    """
    return {
        "total": len(service.image_metadata),
        "events": [
            {
                "event_id": eid,
                "title": meta.get("title"),
                "category": meta.get("analysis", {}).get("categories", [{}])[0].get("label"),
                "mood": meta.get("analysis", {}).get("moods", [{}])[0].get("label"),
                "audience": meta.get("analysis", {}).get("audiences", [{}])[0].get("label"),
                "manual_registered": meta.get("manual_registered", False)
            }
            for eid, meta in service.image_metadata.items()
        ]
    }


@router.delete("/registered/{event_id}")
async def delete_registered_event(
        event_id: int,
        service: VLMService = Depends(get_vlm_service)
):
    """
    등록된 이벤트 삭제 (메모리 + ChromaDB)
    """
    deleted = await service.delete_event(event_id)
    if deleted:
        return {"success": True, "deleted_event_id": event_id}
    else:
        raise HTTPException(status_code=404, detail=f"이벤트 {event_id}를 찾을 수 없습니다")


@router.delete("/registered/all")
async def delete_all_registered(
        service: VLMService = Depends(get_vlm_service)
):
    """
    모든 등록 데이터 삭제 (메모리 + ChromaDB)
    """
    count = await service.delete_all()
    return {"success": True, "deleted_count": count}

# 유틸리티 함수
async def validate_and_load_image(file: UploadFile) -> Image.Image:
    # 파일 타입 검증
    if not file.content_type or not file.content_type.startswith("image/"):
        raise HTTPException(
            status_code=400,
            detail="이미지 파일만 업로드 가능합니다 (JPEG, PNG, GIF, WebP)"
        )

    # 파일 크기 검증
    contents = await file.read()
    size_mb = len(contents) / (1024 * 1024)

    if size_mb > settings.MAX_IMAGE_SIZE_MB:
        raise HTTPException(
            status_code=400,
            detail=f"파일 크기가 너무 큽니다 (최대 {settings.MAX_IMAGE_SIZE_MB}MB)"
        )
    
    # 이미지 로드
    try:
        image = Image.open(BytesIO(contents))
        # RGB 모드로 변환 (RGBA, P 모드 등 처리)
        if image.mode != "RGB":
            image = image.convert("RGB")
        return image
    except Exception as e:
        raise HTTPException(
            status_code=400,
            detail=f"이미지를 읽을 수 없습니다: {str(e)}"
        )
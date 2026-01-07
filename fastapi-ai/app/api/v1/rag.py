from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel, Field
from typing import List, Optional
from datetime import datetime
import logging

from app.models.schemas import (
    SearchRequest,
    SearchResult,
    SearchResponse,
    IndexResponse,
    SimilarEventRequest,
    StatsResponse,
    APIResponse
)
from app.services.rag_service import get_rag_service, RAGService

router = APIRouter(prefix="/rag", tags=["RAG - Semantic Search"])
logger = logging.getLogger(__name__)

@router.post("/search", response_model=SearchResponse)
async def search_events(
    request: SearchRequest,
    service: RAGService = Depends(get_rag_service)
):
    import time
    start_time = time.time()

    try:
        results = await service.search(
            query=request.query,
            top_k=request.top_k,
            category_filter=request.category   
        )

        search_time_ms = (time.time() - start_time) * 1000

        return SearchResponse(
            query=request.query,
            total_results=len(results),
            results=[SearchResult(**r) for r in results],
            search_time_ms=round(search_time_ms, 2),
            searched_at=datetime.now()
        )
    
    except Exception as e:
        logger.error(f"이벤트 검색 실패: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"검색 중 오류 발생: {str(e)}")


@router.get("/search", response_model=SearchResponse)
async def search_events_get(
        query: str = Query(..., min_length=1, max_length=500, description="검색 쿼리"),
        top_k: int = Query(10, ge=1, le=50, description="반환할 결과 수"),
        category: Optional[str] = Query(None, description="카테고리 필터"),
        service: RAGService = Depends(get_rag_service)
):
    import time
    start_time = time.time()

    try:
        results = await service.search(
            query=query,
            top_k=top_k,
            category_filter=category
        )

        search_time_ms = (time.time() - start_time) * 1000

        return SearchResponse(
            query=query,
            total_results=len(results),
            results=[SearchResult(**r) for r in results],
            search_time_ms=round(search_time_ms, 2),
            searched_at=datetime.now()
        )

    except Exception as e:
        logger.error(f"검색 실패: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"검색 중 오류 발생: {str(e)}")


@router.post("/index", response_model=IndexResponse)
async def index_events(
        force_reindex: bool = Query(False, description="기존 데이터 삭제 후 재인덱싱"),
        service: RAGService = Depends(get_rag_service)
):
    try:
        result = await service.index_events(force_reindex=force_reindex)

        return IndexResponse(
            indexed=result.get("indexed", 0),
            total_events=result.get("total_events", 0),
            message=result.get("message", ""),
            indexed_at=datetime.now()
        )

    except Exception as e:
        logger.error(f"인덱싱 실패: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"인덱싱 중 오류 발생: {str(e)}")
    

@router.post("/similar", response_model=SearchResponse)
async def get_similar_events(
        request: SimilarEventRequest,
        service: RAGService = Depends(get_rag_service)
):
    try:
        results = await service.get_similar_events(
            event_id=request.event_id,
            top_k=request.top_k
        )

        return SearchResponse(
            query=f"event_id={request.event_id}와 유사한 이벤트",
            total_results=len(results),
            results=[SearchResult(
                event_id=r["event_id"],
                title=r["title"],
                category=r["category"],
                venue="",
                event_date="",
                similarity_score=r["similarity_score"],
                rank=i + 1
            ) for i, r in enumerate(results)],
            search_time_ms=0,
            searched_at=datetime.now()
        )

    except Exception as e:
        logger.error(f"유사 이벤트 검색 실패: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/stats", response_model=StatsResponse)
async def get_stats(
        service: RAGService = Depends(get_rag_service)
):
    stats = service.get_stats()
    return StatsResponse(**stats)

@router.get("/health")
async def health_check(
        service: RAGService = Depends(get_rag_service)
):
    stats = service.get_stats()
    return {
        "status": "healthy" if stats.get("status") == "initialized" else "initializing",
        "service": "RAG",
        "details": stats
    }
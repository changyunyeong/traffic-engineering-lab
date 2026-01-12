import logging
from typing import List, Dict, Optional
from pathlib import Path
import httpx
import asyncio

from sentence_transformers import SentenceTransformer
import chromadb
from chromadb.config import Settings as ChromaSettings

from app.config import get_settings

settings = get_settings()
logger = logging.getLogger(__name__)


class RAGService:
    ''' rag 기반 검색 서비스'''

    def __init__(self):
        self.embedding_model: Optional[SentenceTransformer] = None
        self.chroma_client: Optional[chromadb.Client] = None
        self.collection = None
        self._initialized = False

    async def initialize(self):
        ''' 모델 및 벡터 db 초기화 '''
        if self._initialized:
            return

        logger.info("RAG 초기화 시작...")

        # 임베딩 모델 로드
        try: 
            self.embedding_model = SentenceTransformer(settings.RAG_EMBEDDING_MODEL)
            logger.info(f"임베딩 모델 로드 완료: {settings.RAG_EMBEDDING_MODEL}")
        except Exception as e:
            logger.error(f"임베딩 모델 로드 실패: {e}")
            raise

        # chromadb 초기화
        try:
            persist_dir = Path(settings.CHROMA_PERSIST_DIR)
            persist_dir.mkdir(parents=True, exist_ok=True)

            self.chroma_client = chromadb.PersistentClient(
                path=str(persist_dir)
            )

            self.collection = self.chroma_client.get_or_create_collection(
                name=settings.CHROMA_COLLECTION_NAME,
                metadata={"description": "이벤트 시맨틱 검색용 벡터 DB"}
            )
            logger.info(f"ChromaDB 초기화 완료: {persist_dir}")
        except Exception as e:
            logger.error(f"ChromaDB 초기화 실패: {e}")
            raise

        self._initialized = True
        logger.info("RAG 서비스 초기화 완료") 

    async def index_events(self, force_reindex: bool = False) -> Dict:
        ''' 
        spring boot에서 이벤트 데이터 가져와 벡터 DB에 인덱싱 
        force_reindex: True면 기존 데이터 삭제 후 재인덱싱
        '''

        await self.initialize()

        # 기존 데이터 삭제
        if force_reindex:
            try:
                self.chroma_client.delete_collection(name=settings.CHROMA_COLLECTION_NAME)
                self.collection = self.chroma_client.get_or_create_collection(
                    name=settings.CHROMA_COLLECTION_NAME
                )
                logger.info("기존 벡터 DB 데이터 삭제 완료")
            except Exception as e:
                logger.error(f"기존 데이터 삭제 실패: {e}")
        
        # spring boot에서 이벤트 데이터 가져오기
        events = await self._fetch_events_from_springboot()

        if not events:
            return {"indexed": 0, "message": "가져온 이벤트 데이터가 없습니다."}
        
        # 벡터 db에 인덱싱
        indexed_count = 0
        batch_size = 100

        for i in range(0, len(events), batch_size):
            batch = events[i:i + batch_size]

            ids = []
            documents = []
            metadatas = []
            embeddings = []

            for event in batch:
                event_id = str(event.get("id"))

                # 검색용 텍스트 생성 (제목 + 설명 + 카테고리 + 장소)
                search_text = self._create_search_text(event)

                # 임베딩 생성
                embedding = self.embedding_model.encode(search_text).tolist()

                ids.append(event_id)
                documents.append(search_text)
                metadatas.append({
                    "event_id": event_id,
                    "title": event.get("title", ""),
                    "category": event.get("category", ""),
                    "venue": event.get("venue", ""),
                    "event_date": event.get("event_date", "")
                })
                embeddings.append(embedding)

            # chromadb에 추가
            try:
                self.collection.upsert(
                    ids=ids,
                    documents=documents,
                    metadatas=metadatas,
                    embeddings=embeddings
                )
                indexed_count += len(batch)
                logger.info(f"인덱싱 진행: {indexed_count}/{len(events)}")
            except Exception as e:
                logger.error(f"벡터 DB 인덱싱 실패: {e}")

            logger.info("이벤트 데이터 인덱싱 완료")
            return {
            "indexed": indexed_count,
            "total_events": len(events),
            "message": "인덱싱 완료"
        }

    async def search(
            self,
            query: str,
            top_k: int = None,
            category_filter: str = None
    ) -> List[Dict]:
        '''
        자연어로 이벤트 시맨틱 검색

        Args:
            query: 검색 질의 ("신나는 록 콘서트", "아이와 함께 볼 공연" 등)
            top_k: 반환할 결과 수
            category_filter: 카테고리 필터 (CONCERT, MUSICAL, SPORTS, ETC)

        Returns:
            검색된 이벤트 목록 (유사도 점수 포함)
        '''
        await self.initialize()

        if top_k is None:
            top_k = settings.RAG_TOP_K

        # 쿼리 임베딩 생성
        query_embedding = self.embedding_model.encode(query).tolist()

        # 필터 조건 설정
        where_filter = None
        if category_filter:
            where_filter = {"category": category_filter}

        # chromadb에서 검색
        try:
            results = self.collection.query(
                query_embeddings=[query_embedding],
                n_results=top_k,
                where=where_filter,
                include=["documents", "metadatas", "distances"]
            )
        except Exception as e:
            logger.error(f"검색 실패: {e}")
            return []   
        
        # 결과 포멧팅
        search_results = []
        if results and results.get("ids") and results["ids"][0]:
            for i, doc_id in enumerate(results["ids"][0]):
                metadata = results["metadatas"][0][i] if results.get("metadatas") else {}
                distance = results["distances"][0][i] if results.get("distances") else []

                # 유사도 점수 계산 (1 - 거리), 높을수록 유사 
                similarity_score = max(0, 1-distance)

                search_results.append({
                    "event_id": metadata.get("event_id"),
                    "title": metadata.get("title", ""),
                    "category": metadata.get("category", ""),
                    "venue": metadata.get("venue", ""),
                    "event_date": metadata.get("event_date", ""),
                    "similarity_score": round(similarity_score, 4),
                    "rank": i + 1
                })
            
        logger.info(f"검색 완료: query='{query}', results={len(search_results)}")
        return search_results
    
    async def get_similar_events(
        self,
        event_id: int,
        top_k: int = 5
    ) -> List[Dict]:
        ''' 특정 이벤트와 유사한 이벤트 검색 '''
        await self.initialize()

        # 해당 이벤트 임베딩 조회
        try:
            result = self.collection.get(
                ids=[str(event_id)],
                include=["embeddings", "documents"]
            )

            if not result or not result.get("embeddings"):
                logger.warning(f"이벤트 ID {event_id}에 대한 임베딩을 찾을 수 없음")
                return []
            
            event_embedding = result["embeddings"][0]

        except Exception as e:
            logger.error(f"이벤트 임베딩 조회 실패: {e}")
            return []
        
        # 유사 이벤트 검색 (자기 자신 제외를 위해 +1)
        results = self.collection.query(
            query_embeddings=[event_embedding],
            n_results=top_k + 1,
            include=["metadatas", "distances"]
        )

        # 자기 자신 제외하고 결과 반환
        similar_events = []
        if results and results.get("ids"):
            for i, doc_id in enumerate(results["ids"][0]):
                if doc_id == str(event_id):
                    continue

                metadata = results["metadatas"][0][i]
                distance = results["distances"][0][i]
                similarity_score = max(0, 1 - distance)

                similar_events.append({
                    "event_id": metadata.get("event_id"),
                    "title": metadata.get("title", ""),
                    "category": metadata.get("category", ""),
                    "similarity_score": round(similarity_score, 4)
                })

                if len(similar_events) >= top_k:
                    break

        return similar_events
    
    def _create_search_text(self, event: Dict) -> str:
        ''' 이벤트 데이터로부터 검색용 텍스트 생성 '''
        parts = []

        if event.get("title"):
            parts.append(event["title"])

        if event.get("description"):
            parts.append(event["description"])

        if event.get("category"):
            # 카테고리 한국어로 변환
            category_kr = {
                "CONCERT": "콘서트",
                "MUSICAL": "뮤지컬",
                "SPORTS": "스포츠",
                "ETC": "기타 공연"
            }.get(event["category"], event["category"])
            parts.append(category_kr)

        if event.get("venue"):
            parts.append(event["venue"])

        return " ".join(parts)
    
    async def _fetch_events_from_springboot(self) -> List[Dict]:
        ''' spring boot에서 이벤트 데이터 비동기 조회 '''
        async with httpx.AsyncClient(timeout=30) as client:
            try:
                response = await client.get(
                    f"{settings.SPRINGBOOT_API_URL}/api/v1/events",
                    params = {"page":0, "size":1000}
                )
                response.raise_for_status()
                data = response.json()

                events = data.get("data", {}).get("content", [])
                logger.info(f"Spring Boot에서 이벤트 데이터 {len(events)}건 조회")
                return events

            except httpx.RequestError as e:
                logger.error(f"Spring Boot API 요청 실패: {e}")
                return self._get_mock_events()
            except Exception as e:
                logger.error(f"이벤트 데이터 조회 실패: {e}")   
                return self._get_mock_events()
            
    def _get_mock_events(self) -> List[Dict]:
        """테스트용 Mock 이벤트 데이터"""
        return [
            {"id": 1, "title": "BTS 월드투어 콘서트", "description": "신나는 K-POP 공연", "category": "CONCERT", "venue": "고척 스카이돔"},
            {"id": 2, "title": "블랙핑크 콘서트", "description": "화려한 걸그룹 공연", "category": "CONCERT", "venue": "KSPO DOME"},
            {"id": 3, "title": "레미제라블", "description": "감동적인 뮤지컬 명작", "category": "MUSICAL", "venue": "블루스퀘어"},
            {"id": 4, "title": "아이유 전국투어", "description": "감성적인 발라드 공연", "category": "CONCERT", "venue": "올림픽홀"},
            {"id": 5, "title": "위키드", "description": "가족과 함께 보기 좋은 뮤지컬", "category": "MUSICAL", "venue": "예술의전당"},
            {"id": 6, "title": "KBO 리그 개막전", "description": "야구 경기", "category": "SPORTS", "venue": "잠실야구장"},
            {"id": 7, "title": "오페라의 유령", "description": "클래식 뮤지컬", "category": "MUSICAL", "venue": "샤롯데씨어터"},
            {"id": 8, "title": "세븐틴 콘서트", "description": "에너지 넘치는 아이돌 공연", "category": "CONCERT", "venue": "고척 스카이돔"},
            {"id": 9, "title": "맘마미아", "description": "신나는 ABBA 음악 뮤지컬", "category": "MUSICAL", "venue": "충무아트센터"},
            {"id": 10, "title": "K리그 클래식", "description": "축구 경기", "category": "SPORTS", "venue": "서울월드컵경기장"},
        ]
    
    def get_stats(self) -> Dict:
        ''' 벡터 db 통계 정보 조회 '''
        if not self._initialized or not self.collection:
            return {"status": "not_initialized", "count": 0}    
        
        try:
            count = self.collection.count()
            return {
                "status": "initialized",
                "collection_name": settings.CHROMA_COLLECTION_NAME,
                "document_count": count,
                "embedding_model": settings.RAG_EMBEDDING_MODEL 
            }
        except Exception as e:
            return {"status": "error", "message": str(e)}
        
 # 싱글톤 인스턴스       
_rag_service: Optional[RAGService] = None

def get_rag_service() -> RAGService:
    global _rag_service
    if _rag_service is None:
        _rag_service = RAGService() # 인스턴스 없으면 새로 생성 
    return _rag_service

'''
싱글톤 사용 시:
첫 호출에만 초기화
이후 호출은 기존 인스턴스 재사용
'''
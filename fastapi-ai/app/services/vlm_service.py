import logging
from typing import List, Dict, Optional, Tuple
from pathlib import Path
import io
import uuid
import asyncio
import json

import torch
import open_clip
from PIL import Image
import numpy as np
import chromadb

from app.config import get_settings

settings = get_settings()
logger = logging.getLogger(__name__)

class VLMService:

    def __init__(self):
        self.model = None
        self.preprocess = None
        self.tokenizer = None
        self.device = None
        self._initialized = False

        # ChromaDB 클라이언트 및 컬렉션
        self.chroma_client: Optional[chromadb.Client] = None
        self.collection = None

        # 메모리 캐시 (빠른 검색용)
        self.image_embeddings: Dict[int, np.ndarray] = {}
        self.image_metadata: Dict[int, Dict] = {}

        # 분석할 텍스트 리스트 정의
        self.mood_prompts = {
            "열정적인": "passionate energetic dynamic exciting powerful 열정 에너지 역동적",
            "신나는": "exciting fun festive cheerful upbeat 신나는 흥겨운 축제",
            "감동적인": "emotional touching moving heartwarming 감동 눈물 서정적",
            "웅장한": "grand magnificent epic spectacular 웅장한 장엄한 스케일",
            "유쾌한": "funny humorous comic entertaining 유쾌한 재미있는",
            "차분한": "calm peaceful serene relaxing 차분한 평화로운 잔잔한",
            "화려한": "glamorous colorful flashy bright stage lights 화려한 현란한",
            "클래식한": "classic elegant sophisticated traditional 클래식 고급스러운 우아한",
            "트렌디한": "trendy hip modern stylish young 트렌디 힙한 젊은",
            "역동적인": "dynamic active sporty athletic action 역동적 스포츠 액션",
        }

        self.category_prompts = {
            "스포츠": "baseball football soccer basketball sports team logo emblem uniform stadium cheering 야구 축구 농구 스포츠",
            "콘서트": "singer idol band concert live performance stage microphone kpop 가수 아이돌 콘서트 무대",
            "뮤지컬": "musical theater actor actress broadway stage costume 뮤지컬 연극 배우 무대",
            "클래식": "orchestra classical music violin piano conductor symphony 오케스트라 클래식 바이올린 피아노",
            "전시회": "art exhibition gallery painting artwork museum 미술 전시회 갤러리 작품",
            "페스티벌": "festival outdoor concert EDM DJ party 페스티벌 축제 야외",
            "연극": "theater play drama actor stage 연극 드라마 배우",
            "오페라": "opera soprano tenor aria classical singing 오페라 성악",
            "코미디": "comedy standup funny show laugh 코미디 개그 웃음",
            "키즈": "kids children family animation character cartoon 어린이 키즈 애니메이션 캐릭터",
        }

        self.audience_prompts = {
            "가족": "family all ages kids parents together 가족 온가족",
            "연인": "couple date romantic love 연인 커플 데이트",
            "친구": "friends group party fun together 친구 그룹",
            "어린이": "children kids young cartoon character 어린이 아이들",
            "성인": "adult mature professional 성인 어른",
            "팬덤": "fans fandom idol cheering lightstick 팬 팬클럽 응원",
        }

        self.mood_labels = list(self.mood_prompts.keys())
        self.category_labels = list(self.category_prompts.keys())
        self.audience_labels = list(self.audience_prompts.keys())


    async def initialize(self):

        if self._initialized:
            return

        logger.info("Initializing VLM model...")

        # gpu 사용 가능 여부 확인
        self.device = "cuda" if torch.cuda.is_available() else "cpu"

        try:
            # clip model load
            self.model, _, self.preprocess = open_clip.create_model_and_transforms(
                settings.VLM_MODEL_NAME,
                pretrained = settings.VLM_PRETRAINED
            )
            self.model = self.model.to(self.device)
            self.model.eval()

            self.tokenizer = open_clip.get_tokenizer(settings.VLM_MODEL_NAME)
            logger.info(f"clip 모델 로드 완료: {settings.VLM_MODEL_NAME}")

        except Exception as e:
            logger.error(f"clip 모델 로드 실패: {e}")
            raise e

        # ChromaDB 초기화
        try:
            persist_dir = Path(settings.CHROMA_PERSIST_DIR)
            persist_dir.mkdir(parents=True, exist_ok=True)

            self.chroma_client = chromadb.PersistentClient(
                path=str(persist_dir)
            )

            self.collection = self.chroma_client.get_or_create_collection(
                name=settings.VLM_CHROMA_COLLECTION_NAME,
                metadata={"description": "VLM 이미지/텍스트 임베딩 저장소"}
            )
            logger.info(f"VLM ChromaDB 초기화 완료: {settings.VLM_CHROMA_COLLECTION_NAME}")

            # 기존 데이터 메모리 캐시로 로드
            await self._load_from_chroma()

        except Exception as e:
            logger.error(f"ChromaDB 초기화 실패: {e}")
            raise e

        # 이미지 저장 디렉토리 생성
        Path(settings.IMAGE_UPLOAD_DIR).mkdir(parents=True, exist_ok=True)

        self._initialized = True
        logger.info("VLM 모델 초기화 완료")

    async def _load_from_chroma(self):
        """ChromaDB에서 기존 데이터를 메모리 캐시로 로드"""
        try:
            count = self.collection.count()
            if count == 0:
                logger.info("ChromaDB에 저장된 VLM 데이터 없음")
                return

            # 모든 데이터 조회
            results = self.collection.get(
                include=["embeddings", "metadatas"]
            )

            if results and results.get("ids"):
                for i, doc_id in enumerate(results["ids"]):
                    event_id = int(doc_id)
                    embedding = np.array(results["embeddings"][i])
                    metadata_raw = results["metadatas"][i]

                    # 메타데이터 파싱 (JSON 문자열로 저장된 경우)
                    metadata = {}
                    if metadata_raw.get("metadata_json"):
                        metadata = json.loads(metadata_raw["metadata_json"])
                    else:
                        metadata = metadata_raw

                    self.image_embeddings[event_id] = embedding
                    self.image_metadata[event_id] = metadata

            logger.info(f"ChromaDB에서 {len(self.image_embeddings)}건 로드 완료")

        except Exception as e:
            logger.error(f"ChromaDB 데이터 로드 실패: {e}")

    async def _save_to_chroma(self, event_id: int, embedding: np.ndarray, metadata: Dict):
        """ChromaDB에 데이터 저장"""
        try:
            # 메타데이터를 JSON 문자열로 변환 (복잡한 구조 지원)
            metadata_json = json.dumps(metadata, ensure_ascii=False)

            self.collection.upsert(
                ids=[str(event_id)],
                embeddings=[embedding.tolist()],
                metadatas=[{"metadata_json": metadata_json, "event_id": event_id}]
            )
            logger.debug(f"이벤트 {event_id} ChromaDB 저장 완료")

        except Exception as e:
            logger.error(f"ChromaDB 저장 실패 (event_id={event_id}): {e}")

    async def _delete_from_chroma(self, event_id: int):
        """ChromaDB에서 데이터 삭제"""
        try:
            self.collection.delete(ids=[str(event_id)])
            logger.debug(f"이벤트 {event_id} ChromaDB에서 삭제 완료")
        except Exception as e:
            logger.error(f"ChromaDB 삭제 실패 (event_id={event_id}): {e}")

    async def encode_image(self, image: Image.Image) -> np.ndarray:

        await self.initialize()

        # 이미지 전처리
        image_input = self.preprocess(image).unsqueeze(0).to(self.device)

        # 임베딩 생성
        with torch.no_grad():
            image_features = self.model.encode_image(image_input)
            image_features /= image_features.norm(dim=-1, keepdim=True)

        return image_features.cpu().numpy().flatten()

    async def encode_text(self, text: str) -> np.ndarray:

        await self.initialize()

        # 텍스트 토큰화
        text_tokens = self.tokenizer([text]).to(self.device)

        # 임베딩 생성
        with torch.no_grad():
            text_features = self.model.encode_text(text_tokens)
            text_features /= text_features.norm(dim=-1, keepdim=True)

        return text_features.cpu().numpy().flatten()

    async def analyze_image(self, image: Image.Image) -> Dict:
        '''
        이미지 분석 - 분위기, 카테고리, 타겟 관객 추론

        Returns:
            {
                "moods": [{"label": "신나는", "score": 0.85}, ...],
                "categories": [{"label": "콘서트", "score": 0.92}, ...],
                "audiences": [{"label": "친구", "score": 0.78}, ...],
                "description": "에너지 넘치는 콘서트 포스터입니다."
            }
        '''

        await self.initialize()

        image_embedding = await self.encode_image(image)

        # 유사도 계산
        moods = await self._classify_with_lables(
            image_embedding,
            [f"{m} 분위기의 포스터" for m in self.mood_labels],
            self.mood_labels
        )

        categories = await self._classify_with_lables(
            image_embedding,
            [f"{c} 포스터" for c in self.category_labels],
            self.category_labels
        )

        audiences = await self._classify_with_lables(
            image_embedding,
            [f"{a} 와 함께 보기 좋은 공연" for a in self.audience_labels],
            self.audience_labels
        )

        # 설명 생성
        top_mood = moods[0]["label"] if moods else "다양한"
        top_category = categories[0]["label"] if categories else "공연"
        top_audience = audiences[0]["label"] if audiences else "모든"

        description = f"{top_mood} 분위기의 {top_category} 포스터 입니다. {top_audience}과 함께 관람하기 좋습니다."

        return {
            "moods": moods[:5],
            "categories": categories[:5],
            "audiences": audiences[:3],
            "description": description
        }

    async def _classify_with_lables(
            self,
            image_embedding: np.ndarray,
            prompts: List[str],
            labels: List[str]
            ) -> List[Dict]:
        ''' 유사도 계산 '''

        scores = []

        for prompt in prompts:
            text_embedding = await self.encode_text(prompt)
            similarity = np.dot(image_embedding, text_embedding)
            scores.append(float(similarity))

        # 정규화
        scores_array = np.array(scores)
        exp_scores = np.exp(scores_array - np.max(scores_array))
        probs = exp_scores / exp_scores.sum()

        # 점수순 정렬
        results = [
            {"label": label, "score": round(float(prob), 4)}
            for label, prob in zip(labels, probs)
        ]
        results.sort(key=lambda x: x["score"], reverse=True)

        return results

    async def register_event_image(
            self,
            event_id: int,
            image: Image.Image,
            metadata: Dict = None
    ) -> Dict:
        ''' 이벤트 포스터 이미지 등록 '''

        await self.initialize()

        embedding = await self.encode_image(image)
        analysis = await self.analyze_image(image)

        # 메모리 캐시에 저장
        self.image_embeddings[event_id] = embedding
        self.image_metadata[event_id] = {
            "event_id": event_id,
            "analysis": analysis,
            **(metadata or {})
        }

        # ChromaDB에 영구 저장
        await self._save_to_chroma(event_id, embedding, self.image_metadata[event_id])

        #  이미지 파일 저장
        image_path = Path(settings.IMAGE_UPLOAD_DIR) / f"event_{event_id}.jpg"
        image.save(image_path, "JPEG", quality=85)

        logger.info(f"이벤트 {event_id} 이미지 등록 완료")

        return {
            "event_id": event_id,
            "analysis": analysis,
            "image_path": str(image_path)
        }

    async def register_manual(
            self,
            event_id: int,
            embedding: np.ndarray,
            metadata: Dict
    ):
        """수동 등록 데이터 저장 (API에서 호출)"""
        await self.initialize()

        # 메모리 캐시에 저장
        self.image_embeddings[event_id] = embedding
        self.image_metadata[event_id] = metadata

        # ChromaDB에 영구 저장
        await self._save_to_chroma(event_id, embedding, metadata)

        logger.debug(f"이벤트 {event_id} 수동 등록 완료 (ChromaDB 저장)")

    async def search_by_image(
            self,
            image: Image.Image,
            top_k: int = 5
    ) -> List[Dict]:
        ''' 이미지로 유사한 이벤트 검색 '''

        await self.initialize()

        if not self.image_embeddings:
            logger.warning("등록된 이미지 임베딩이 없습니다")
            return []

        # 검색 이미지 임베딩
        query_embedding = await self.encode_image(image)

        # 유사도 계산
        similarities = []
        for event_id, embedding in self.image_embeddings.items():
            similartiy = np.dot(query_embedding, embedding)
            similarities.append({
                "event_id": event_id,
                "similarity_score": float(similartiy),
                "metadata": self.image_metadata.get(event_id, {})
            })

        # 유사도 순 정렬
        similarities.sort(key=lambda x: x["similarity_score"], reverse=True)

        return similarities[:top_k]

    async def search_by_text(
            self,
            query: str,
            top_k: int = 5
    ) -> List[Dict]:
        ''' 텍스트로 유사한 이미지 검색 '''

        await self.initialize()

        if not self.image_embeddings:
            logger.warning("등록된 이벤트 이미지가 없습니다")
            return []

        # 텍스트 임베딩
        query_embedding = await self.encode_text(query)

        # 유사도 계산
        similarities = []
        for event_id, image_embedding in self.image_embeddings.items():
            similarity = np.dot(query_embedding, image_embedding)
            similarities.append({
                "event_id": event_id,
                "similarity_score": float(similarity),
                "metadata": self.image_metadata.get(event_id, {})
            })

        # 유사도순 정렬
        similarities.sort(key=lambda x: x["similarity_score"], reverse=True)

        return similarities[:top_k]

    async def get_similar_events(
            self,
            event_id: int,
            top_k: int = 5
    ) -> List[Dict]:
        ''' 특정 이벤트와 유사한 이벤트 검색 '''

        if event_id not in self.image_embeddings:
            logger.warning(f"이벤트 {event_id} 의 이미지가 등록되지 않았습니다")
            return []

        query_embedding = self.image_embeddings[event_id]

        # 유사도 계산
        similarities = []
        for eid, embedding in self.image_embeddings.items():
            if eid == event_id:
                continue
            similaritiy = np.dot(query_embedding, embedding)
            similarities.append({
                "event_id": eid,
                "similarity_score": float(similaritiy),
                "metadata": self.image_metadata.get(eid, {})
            })

        similarities.sort(key=lambda x: x["similarity_score"], reverse=True)
        return similarities[:top_k]

    async def delete_event(self, event_id: int) -> bool:
        """이벤트 데이터 삭제 (메모리 + ChromaDB)"""
        await self.initialize()

        if event_id in self.image_metadata:
            del self.image_metadata[event_id]
            if event_id in self.image_embeddings:
                del self.image_embeddings[event_id]

            # ChromaDB에서도 삭제
            await self._delete_from_chroma(event_id)
            return True
        return False

    async def delete_all(self) -> int:
        """모든 데이터 삭제 (메모리 + ChromaDB)"""
        await self.initialize()

        count = len(self.image_metadata)

        # 메모리 캐시 삭제
        self.image_metadata.clear()
        self.image_embeddings.clear()

        # ChromaDB 컬렉션 재생성
        try:
            self.chroma_client.delete_collection(name=settings.VLM_CHROMA_COLLECTION_NAME)
            self.collection = self.chroma_client.get_or_create_collection(
                name=settings.VLM_CHROMA_COLLECTION_NAME,
                metadata={"description": "VLM 이미지/텍스트 임베딩 저장소"}
            )
            logger.info("ChromaDB 컬렉션 초기화 완료")
        except Exception as e:
            logger.error(f"ChromaDB 컬렉션 초기화 실패: {e}")

        return count

    def get_stats(self) -> Dict:
        """서비스 통계"""
        chroma_count = 0
        if self.collection:
            try:
                chroma_count = self.collection.count()
            except:
                pass

        return {
            "status": "initialized" if self._initialized else "not_initialized",
            "device": str(self.device) if self.device else "unknown",
            "model": settings.VLM_MODEL_NAME,
            "registered_images": len(self.image_embeddings),
            "chroma_count": chroma_count,
            "mood_labels": len(self.mood_labels),
            "category_labels": len(self.category_labels)
        }

_vlm_service: Optional[VLMService] = None


def get_vlm_service() -> VLMService:
    global _vlm_service
    if _vlm_service is None:
        _vlm_service = VLMService()
    return _vlm_service

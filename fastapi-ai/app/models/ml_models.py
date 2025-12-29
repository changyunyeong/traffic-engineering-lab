import numpy as np
import pandas as pd
from sklearn.neighbors import NearestNeighbors
from sklearn.ensemble import IsolationForest
from sklearn.preprocessing import StandardScaler
import joblib
from pathlib import Path
from typing import List, Tuple, Optional
import logging

logger = logging.getLogger(__name__)


class RecommendationModel:
    '''
    협업 필터링 기반 추천 모델
    NearestNeighbors: 별도의 모델 생성없이 인접 데이터를 분류 또는 예측
    유클리드 거리 대신 코사인 (벡터의 각도)로 유사도 측정 
    '''        
    def __init__(self, n_neighbors: int = 10):
        self.n_neighbors = n_neighbors
        self.model = NearestNeighbors(n_neighbors=n_neighbors, metric='cosine', algorithm='brute')
        self.user_event_matrix = None
        self.user_ids = None
        self.event_ids = None
        self.is_fitted = False
    
    def fit(self, user_event_interactions: pd.DataFrame):

        # 피벗 테이블 생성 (행: 사용자, 열: 이벤트)
        pivot = user_event_interactions.pivot_table(
            index='user_id',
            columns='event_id',
            values='interaction_score',
            fill_value=0
        )
        
        self.user_event_matrix = pivot.values
        self.user_ids = pivot.index.tolist()
        self.event_ids = pivot.columns.tolist()
        
        # 모델 학습
        self.model.fit(self.user_event_matrix)
        self.is_fitted = True
        
        logger.info(f"추천 모델 학습 완료: {len(self.user_ids)}명 사용자, {len(self.event_ids)}개 이벤트")
    
    def recommend(self, user_id: int, n_recommendations: int = 10) -> List[Tuple[int, float]]:
        '''
        특정 사용자에게 이벤트 추천
        '''
        if not self.is_fitted:
            raise ValueError("모델이 학습되지 않았습니다")
        
        if user_id not in self.user_ids:
            logger.warning(f"사용자 {user_id}의 데이터가 없습니다")
            return self._recommend_popular(n_recommendations)
        
        # 사용자 인덱스 찾기
        user_idx = self.user_ids.index(user_id)
        user_vector = self.user_event_matrix[user_idx].reshape(1, -1)
        
        # 유사 사용자 찾기
        distances, indices = self.model.kneighbors(user_vector)
        similar_users = indices[0][1:]  # 자기 자신 제외
        
        # 유사 사용자들이 좋아한 이벤트 집계
        recommendations = {}
        for similar_user_idx in similar_users:
            similar_user_events = self.user_event_matrix[similar_user_idx]
            
            for event_idx, score in enumerate(similar_user_events):
                if score > 0:
                    event_id = self.event_ids[event_idx]
                    
                    # 이미 본 이벤트는 제외
                    if self.user_event_matrix[user_idx][event_idx] == 0:
                        recommendations[event_id] = recommendations.get(event_id, 0) + score
        
        # 점수 순으로 정렬
        sorted_recommendations = sorted(
            recommendations.items(),
            key=lambda x: x[1],
            reverse=True
        )[:n_recommendations]
        
        # 점수 정규화 (0~1)
        if sorted_recommendations:
            max_score = max(score for _, score in sorted_recommendations)
            sorted_recommendations = [
                (event_id, score / max_score)
                for event_id, score in sorted_recommendations
            ]
        
        return sorted_recommendations
    
    def _recommend_popular(self, n: int) -> List[Tuple[int, float]]:
        # 인기 이벤트 추천 (Cold Start 대응)
        event_scores = self.user_event_matrix.sum(axis=0)
        top_events = np.argsort(event_scores)[-n:][::-1]
        
        max_score = event_scores.max()
        return [
            (self.event_ids[idx], event_scores[idx] / max_score)
            for idx in top_events
        ]
    
    def save(self, path: str):
        # 모델 저장
        joblib.dump({
            'model': self.model,
            'user_event_matrix': self.user_event_matrix,
            'user_ids': self.user_ids,
            'event_ids': self.event_ids,
            'is_fitted': self.is_fitted
        }, path)
        logger.info(f"추천 모델 저장: {path}")
    
    @classmethod
    def load(cls, path: str) -> 'RecommendationModel':
        # 모델 로드
        data = joblib.load(path)
        instance = cls()
        instance.model = data['model']
        instance.user_event_matrix = data['user_event_matrix']
        instance.user_ids = data['user_ids']
        instance.event_ids = data['event_ids']
        instance.is_fitted = data['is_fitted']
        logger.info(f"추천 모델 로드: {path}")
        return instance


class AnomalyDetectionModel:
    '''
    isolation Forest 기반 이상 탐지 모델
    Decision Tree를 이용해 isolation 시키는 방식으로 이상치(Anomaly)를 찾아내는 비지도 학습 알고리즘
    tree의 depth가 얕은 것을 먼저 제거 
    '''
    
    def __init__(self, contamination: float = 0.1):
        self.contamination = contamination
        self.model = IsolationForest(
            contamination=contamination,
            random_state=42,
            n_estimators=100
        )
        self.scaler = StandardScaler()
        self.feature_names = None
        self.is_fitted = False
    
    def fit(self, features_df: pd.DataFrame):
        '''
        이상 탐지 모델 학습
        '''
        self.feature_names = features_df.columns.tolist()
        
        # 특성 스케일링
        features_scaled = self.scaler.fit_transform(features_df)
        
        # 모델 학습
        self.model.fit(features_scaled)
        self.is_fitted = True
        
        logger.info(f"이상 탐지 모델 학습 완료: {len(features_df)}개 샘플, {len(self.feature_names)}개 특성")
    
    def predict(self, features_df: pd.DataFrame) -> Tuple[np.ndarray, np.ndarray]:
        '''
        이상 탐지 예측
        
        결과:
            predictions: -1(이상), 1(정상)
            scores: 이상 점수 (-1~1, 음수일수록 이상)
        '''
        if not self.is_fitted:
            raise ValueError("모델이 학습되지 않았습니다")
        
        # 특성 스케일링
        features_scaled = self.scaler.transform(features_df)
        
        # 예측
        predictions = self.model.predict(features_scaled)
        scores = self.model.score_samples(features_scaled)
        
        return predictions, scores
    
    def save(self, path: str):
        # 모델 저장
        joblib.dump({
            'model': self.model,
            'scaler': self.scaler,
            'feature_names': self.feature_names,
            'is_fitted': self.is_fitted
        }, path)
        logger.info(f"이상 탐지 모델 저장: {path}")
    
    @classmethod
    def load(cls, path: str) -> 'AnomalyDetectionModel':
        # 모델 로드
        data = joblib.load(path)
        instance = cls()
        instance.model = data['model']
        instance.scaler = data['scaler']
        instance.feature_names = data['feature_names']
        instance.is_fitted = data['is_fitted']
        logger.info(f"이상 탐지 모델 로드: {path}")
        return instance

class AnomalyDetectionModel:
    """이상 거래 탐지 모델 (Isolation Forest)"""
    
    def __init__(self):
        self.model = IsolationForest(
            contamination=0.1,  # 10%를 이상치로 간주
            random_state=42,
            n_estimators=100
        )
        self.scaler = StandardScaler()
        self.is_fitted = False
        self.feature_names = []
    
    def _extract_features(self, df: pd.DataFrame) -> pd.DataFrame:
        """예약 데이터에서 특징 추출"""
        features = pd.DataFrame()
        
        # 1. 시간 기반 특징
        # features['hour'] = pd.to_datetime(df['purchase_time']).dt.hour
        # features['day_of_week'] = pd.to_datetime(df['purchase_time']).dt.dayofweek
        # features['is_weekend'] = features['day_of_week'].isin([5, 6]).astype(int)
        # features['is_night'] = features['hour'].between(0, 6).astype(int)
        
        # 2. 가격 기반 특징
        # features['price'] = df['price']
        # features['price_log'] = np.log1p(df['price'])
        
        # 3. 사용자 행동 특징
        user_counts = df.groupby('user_id').size()
        features['user_reservation_count'] = df['user_id'].map(user_counts)
        
        event_counts = df.groupby('event_id').size()
        features['event_popularity'] = df['event_id'].map(event_counts)
        
        # 4. IP 기반 특징 (동일 IP에서 여러 예약)
        if 'ip_address' in df.columns:
            ip_counts = df.groupby('ip_address').size()
            features['ip_reservation_count'] = df['ip_address'].map(ip_counts)
        else:
            features['ip_reservation_count'] = 1
        
        # 5. 시간 간격 특징
        # df_sorted = df.sort_values('purchase_time')
        # time_diffs = pd.to_datetime(df_sorted['purchase_time']).diff().dt.total_seconds()
        # features['time_since_last'] = time_diffs.fillna(3600)  # 1시간 기본값
        
        self.feature_names = features.columns.tolist()
        return features
    
    def fit(self, training_data: pd.DataFrame):
        """모델 학습"""
        logger.info(f"이상 탐지 모델 학습 시작: {len(training_data)}건")
        
        # 특징 추출
        X = self._extract_features(training_data)
        
        # 스케일링
        X_scaled = self.scaler.fit_transform(X)
        
        # 모델 학습
        self.model.fit(X_scaled)
        self.is_fitted = True
        
        logger.info("이상 탐지 모델 학습 완료")
        return self
    
    def predict(self, data: pd.DataFrame) -> Tuple[np.ndarray, np.ndarray]:
        """
        이상치 예측
        Returns:
            predictions: -1(이상) 또는 1(정상)
            scores: 이상치 점수 (낮을수록 이상)
        """
        if not self.is_fitted:
            raise ValueError("모델이 학습되지 않았습니다")
        
        X = self._extract_features(data)
        X_scaled = self.scaler.transform(X)
        
        predictions = self.model.predict(X_scaled)
        scores = self.model.score_samples(X_scaled)
        
        return predictions, scores
    
    def save(self, path: str):
        """모델 저장"""
        model_data = {
            'model': self.model,
            'scaler': self.scaler,
            'feature_names': self.feature_names,
            'is_fitted': self.is_fitted
        }
        joblib.dump(model_data, path)
        logger.info(f"모델 저장 완료: {path}")
    
    @classmethod
    def load(cls, path: str) -> 'AnomalyDetectionModel':
        """모델 로드"""
        model_data = joblib.load(path)
        
        instance = cls()
        instance.model = model_data['model']
        instance.scaler = model_data['scaler']
        instance.feature_names = model_data['feature_names']
        instance.is_fitted = model_data['is_fitted']
        
        logger.info(f"모델 로드 완료: {path}")
        return instance
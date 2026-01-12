FROM python:3.11-slim

WORKDIR /app

# 시스템 의존성 설치
RUN apt-get update && apt-get install -y \
    gcc \
    g++ \
    libgl1 \
    libglib2.0-0 \
    libsm6 \
    libxext6 \
    libxrender-dev \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Python 의존성 설치
COPY requirements.txt .

# PyTorch CPU 버전 먼저 설치 
RUN pip install --no-cache-dir torch torchvision --index-url https://download.pytorch.org/whl/cpu

# 나머지 의존성 설치
RUN pip install --no-cache-dir -r requirements.txt

# 앱 코드 복사
COPY app/ ./app/
COPY .env .env

# 모델 디렉토리 생성
RUN mkdir -p /app/data/models /app/data/chroma_db /app/data/images

EXPOSE 8000

# 메모리 최적화 옵션
ENV PYTORCH_CUDA_ALLOC_CONF=max_split_size_mb:512
ENV OMP_NUM_THREADS=4

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
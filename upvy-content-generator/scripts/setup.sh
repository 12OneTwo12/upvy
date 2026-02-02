#!/bin/bash
# =============================================================================
# Upvy AI Content Generator - 초기 설정 스크립트
# =============================================================================

set -e

echo "🚀 Upvy AI Content Generator 설정 시작..."

# -----------------------------------------------------------------------------
# 1. 환경 변수 파일 확인
# -----------------------------------------------------------------------------
if [ ! -f .env ]; then
    echo "📝 .env 파일 생성 중..."
    cp .env.example .env
    echo "⚠️  .env 파일을 편집하여 환경 변수를 설정해주세요."
    echo "   특히 다음 항목은 필수입니다:"
    echo "   - N8N_PASSWORD"
    echo "   - GCP_PROJECT_ID"
    echo ""
fi

# -----------------------------------------------------------------------------
# 2. GCP 크레덴셜 확인
# -----------------------------------------------------------------------------
if [ ! -f credentials/gcp-key.json ]; then
    echo "⚠️  GCP 서비스 계정 키가 필요합니다."
    echo "   다음 명령어로 키를 생성하세요:"
    echo ""
    echo "   gcloud iam service-accounts keys create credentials/gcp-key.json \\"
    echo "     --iam-account=your-service-account@your-project.iam.gserviceaccount.com"
    echo ""
    echo "   또는 기존 키를 credentials/gcp-key.json에 복사하세요."
    echo ""
fi

# -----------------------------------------------------------------------------
# 3. Docker 확인
# -----------------------------------------------------------------------------
if ! command -v docker &> /dev/null; then
    echo "❌ Docker가 설치되어 있지 않습니다."
    echo "   https://docs.docker.com/get-docker/ 에서 설치해주세요."
    exit 1
fi

if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
    echo "❌ Docker Compose가 설치되어 있지 않습니다."
    exit 1
fi

echo "✅ Docker 확인 완료"

# -----------------------------------------------------------------------------
# 4. Compose Service 빌드
# -----------------------------------------------------------------------------
echo "🔨 Compose Service 빌드 중..."
cd compose-service

if [ -f gradlew ]; then
    ./gradlew bootJar --no-daemon
else
    echo "⚠️  Gradle Wrapper가 없습니다. 먼저 생성해주세요:"
    echo "   cd compose-service && gradle wrapper"
fi

cd ..

# -----------------------------------------------------------------------------
# 5. Docker 이미지 빌드
# -----------------------------------------------------------------------------
echo "🐳 Docker 이미지 빌드 중..."
docker-compose build

# -----------------------------------------------------------------------------
# 완료
# -----------------------------------------------------------------------------
echo ""
echo "✅ 설정 완료!"
echo ""
echo "📋 다음 단계:"
echo "   1. .env 파일 편집"
echo "   2. credentials/gcp-key.json 복사"
echo "   3. docker-compose up -d 실행"
echo ""
echo "🔗 접속 URL:"
echo "   - n8n UI: http://localhost:5678"
echo "   - Compose API: http://localhost:8084"
echo ""

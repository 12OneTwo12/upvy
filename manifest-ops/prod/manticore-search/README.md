# Upvy Manticore Search - 운영 환경

upvy 플랫폼의 콘텐츠/사용자 검색을 위한 Manticore Search Kubernetes manifest입니다.

## 주요 기능

- Manticore Search 서버 실행
- 매시간 자동 인덱싱 작업 수행 (cron)
- MySQL 프로토콜을 통한 인덱스 리로드
- Kubernetes 클러스터 내부 서비스로 배포

## 인덱스 구성

### content_index
콘텐츠(비디오/사진) 검색 인덱스
- **검색 대상 필드**: title, description
- **필터 필드**: category, language, content_type, status
- **정렬 필드**: created_at_unix

### user_index
사용자 검색 인덱스
- **검색 대상 필드**: nickname, bio
- **필터 필드**: follower_count, following_count
- **정렬 필드**: created_at_unix

## 시스템 요구사항

- Kubernetes Cluster
- kubectl 설정
- GitHub Actions (CI/CD)

## 배포 방법

### GitHub Actions를 통한 자동 배포 (권장)

1. GitHub 저장소의 Actions 탭으로 이동
2. "Upvy Manticore Deploy" workflow 선택
3. "Run workflow" 클릭
4. 환경 선택: `prod` 선택
5. "Run workflow" 버튼 클릭

자동으로 다음 작업이 수행됩니다:
- Docker 이미지 빌드 및 GCP Artifact Registry에 푸시
- `prod/manticore-search/deployment.yaml`의 이미지 태그 자동 업데이트
- 커밋 SHA를 이미지 태그로 사용

### 수동 배포 (로컬)

#### 1. ConfigMap 생성

```bash
# template에서 실제 값 치환하여 configmap.yaml 생성
export MYSQL_HOST="your-mysql-host"
export MYSQL_PORT="3306"
export MYSQL_DATABASE="your-database"
export DB_USERNAME="your-username"
export DB_PASSWORD="your-password"

envsubst < configmap.yaml.template > configmap.yaml
```

#### 2. 이미지 빌드 및 푸시

```bash
cd prod/manticore-search

# 이미지 빌드
docker build -t asia-northeast3-docker.pkg.dev/upvy-prod/upvy-prod/upvy-manticore-search:IMAGE_TAG .

# 이미지 푸시
docker push asia-northeast3-docker.pkg.dev/upvy-prod/upvy-prod/upvy-manticore-search:IMAGE_TAG
```

#### 3. Deployment 이미지 태그 업데이트

`deployment.yaml` 파일에서 `IMAGE_TAG`를 실제 이미지 태그로 변경:

```yaml
image: asia-northeast3-docker.pkg.dev/upvy-prod/upvy-prod/upvy-manticore-search:YOUR_TAG
```

#### 4. Kubernetes 리소스 배포

```bash
# ConfigMap 적용
kubectl apply -f configmap.yaml

# Deployment 적용
kubectl apply -f deployment.yaml

# Service 적용
kubectl apply -f service.yaml
```

#### 5. 배포 확인

```bash
# Pod 상태 확인
kubectl get pods -l app=upvy-manticore-search-prod

# 로그 확인
kubectl logs -l app=upvy-manticore-search-prod -f

# Service 확인
kubectl get svc upvy-manticore-search-prod-svc
```

## 프로젝트 구조

- `Dockerfile`: Manticore 이미지 빌드 파일 (cron 포함)
- `start.sh`: 컨테이너 시작 스크립트 (cron 시작 + searchd 실행)
- `configmap.yaml.template`: Manticore 설정 파일 템플릿
- `deployment.yaml`: Kubernetes Deployment 설정
- `service.yaml`: Kubernetes Service 설정 (ClusterIP)

## 자동 인덱싱 설정

이 프로젝트는 cron을 사용하여 매시간 다음 명령을 자동으로 실행합니다:

```bash
indexer content_index --config /etc/manticoresearch/manticore.conf --rotate
indexer user_index --config /etc/manticoresearch/manticore.conf --rotate
mysql -h 127.0.0.1 -P9306 -e "RELOAD INDEXES;"
```

## 접근 방법

### 클러스터 내부에서 접근

- HTTP API: `http://upvy-manticore-search-prod-svc.default.svc.cluster.local:9308`
- MySQL 프로토콜: `mysql -h upvy-manticore-search-prod-svc.default.svc.cluster.local -P 9306`

### 백엔드 설정

```
MANTICORE_BASE_URL=http://upvy-manticore-search-prod-svc:9308
```

### 로컬에서 테스트 (포트포워딩)

```bash
# HTTP API 포트포워딩
kubectl port-forward svc/upvy-manticore-search-prod-svc 9308:9308

# MySQL 프로토콜 포트포워딩
kubectl port-forward svc/upvy-manticore-search-prod-svc 9306:9306

# 접근 테스트
curl http://localhost:9308
mysql -h 127.0.0.1 -P 9306
```

## 인덱싱 작업 확인 방법

인덱싱 작업이 제대로 실행되고 있는지 확인하려면:

```bash
# Pod 접속
kubectl exec -it deployment/upvy-manticore-search-prod -- bash

# cron 설정 확인
crontab -l

# 로그 확인
cat /var/log/manticore/searchd.log
cat /var/log/manticore/query.log
cat /var/log/manticore/cron.log
```

## 문제 해결

### 인덱싱이 실행되지 않는 경우

```bash
# Pod 접속
kubectl exec -it deployment/upvy-manticore-search-prod -- bash

# cron 서비스 확인
service cron status

# 수동으로 인덱싱 명령 실행
indexer content_index --config /etc/manticoresearch/manticore.conf --rotate
indexer user_index --config /etc/manticoresearch/manticore.conf --rotate
mysql -h 127.0.0.1 -P9306 -e "RELOAD INDEXES;"
```

### Pod 시작 실패

```bash
# Pod 상태 확인
kubectl describe pod -l app=upvy-manticore-search-prod

# 로그 확인
kubectl logs -l app=upvy-manticore-search-prod --previous
```

## 리소스 설정

- CPU Request: 200m
- CPU Limit: 500m
- Memory Request: 512Mi
- Memory Limit: 1Gi

## 참고사항

- 이 서비스는 ClusterIP 타입으로 클러스터 내부에서만 접근 가능합니다
- Ingress는 설정되어 있지 않습니다 (내부 서비스용)
- 데이터는 emptyDir 볼륨에 저장됩니다 (Pod 재시작 시 초기화)
- 영구 저장이 필요한 경우 PersistentVolume 사용을 고려하세요
- CI/CD: GitHub Actions를 통한 자동 배포 지원

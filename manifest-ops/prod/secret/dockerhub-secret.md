# Docker Hub imagePullSecret

Docker Hub에서 private 이미지를 pull하기 위한 Secret입니다.

## 생성 방법

```bash
kubectl create secret docker-registry dockerhub-secret \
  --docker-server=https://index.docker.io/v1/ \
  --docker-username=<DOCKERHUB_USERNAME> \
  --docker-password=<DOCKERHUB_TOKEN> \
  --docker-email=<EMAIL> \
  -n default
```

## 생성 확인

```bash
kubectl get secret dockerhub-secret -o yaml
```

## 참고

- `deployment.yaml`에서 `imagePullSecrets: dockerhub-secret`으로 참조
- Docker Hub Access Token은 https://hub.docker.com/settings/security 에서 생성

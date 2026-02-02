#!/bin/sh
# =============================================================================
# n8n 초기화 스크립트
# =============================================================================
# 워크플로우 자동 import
#
# 주의: n8n은 첫 실행시 Owner 계정 설정이 필수입니다 (보안 요구사항)
#       설정 후 볼륨이 유지되면 다시 설정할 필요 없습니다
# =============================================================================

WORKFLOW_FILE="/workflows/content-generator.json"
MARKER_FILE="/home/node/.n8n/.workflow-imported"

# 백그라운드에서 워크플로우 import
import_workflow_background() {
    (
        echo "[init] Waiting for n8n to start..."
        sleep 20

        # n8n 준비 대기
        for i in 1 2 3 4 5 6 7 8 9 10; do
            if wget -q --spider http://localhost:5678/healthz 2>/dev/null; then
                echo "[init] n8n is ready!"
                break
            fi
            echo "[init] Waiting... ($i/10)"
            sleep 5
        done

        # 워크플로우 import (이미 import된 경우 스킵)
        if [ -f "$WORKFLOW_FILE" ] && [ ! -f "$MARKER_FILE" ]; then
            echo "[init] Importing workflow from $WORKFLOW_FILE..."
            if /usr/local/bin/n8n import:workflow --input="$WORKFLOW_FILE" 2>&1; then
                touch "$MARKER_FILE"
                echo "[init] Workflow imported successfully!"
            else
                echo "[init] Warning: Failed to import workflow (may need owner setup first)"
            fi
        elif [ -f "$MARKER_FILE" ]; then
            echo "[init] Workflow already imported"
        fi
    ) &
}

# 백그라운드 작업 시작
import_workflow_background

# 기본 n8n 엔트리포인트 실행
exec /docker-entrypoint.sh "$@"

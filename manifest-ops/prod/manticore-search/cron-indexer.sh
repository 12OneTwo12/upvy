#!/bin/bash

# Manticore Search Index Rotation Script
# 매시간 실행되어 모든 인덱스를 rotate하고 리로드합니다.

LOG_FILE="/var/log/manticore/cron.log"
CONFIG="/etc/manticoresearch/manticore.conf"

# 시작 로그
echo "[$(date +"%Y-%m-%d %H:%M:%S")] Starting scheduled indexing..." >> "$LOG_FILE"

# 인덱스 목록
INDEXES=(
    "content_index"
    "user_index"
    "autocomplete_index"
    "tag_index"
)

# 각 인덱스 rotate
SUCCESS=true
for index in "${INDEXES[@]}"; do
    echo "[$(date +"%Y-%m-%d %H:%M:%S")] Rotating $index..." >> "$LOG_FILE"

    if indexer "$index" --config "$CONFIG" --rotate >> "$LOG_FILE" 2>&1; then
        echo "[$(date +"%Y-%m-%d %H:%M:%S")] Successfully rotated $index" >> "$LOG_FILE"
    else
        echo "[$(date +"%Y-%m-%d %H:%M:%S")] ERROR: Failed to rotate $index" >> "$LOG_FILE"
        SUCCESS=false
    fi
done

# Manticore에 인덱스 리로드 명령
echo "[$(date +"%Y-%m-%d %H:%M:%S")] Reloading indexes in Manticore..." >> "$LOG_FILE"
if mysql -h 127.0.0.1 -P9306 -e "RELOAD INDEXES;" >> "$LOG_FILE" 2>&1; then
    echo "[$(date +"%Y-%m-%d %H:%M:%S")] Successfully reloaded indexes" >> "$LOG_FILE"
else
    echo "[$(date +"%Y-%m-%d %H:%M:%S")] ERROR: Failed to reload indexes" >> "$LOG_FILE"
    SUCCESS=false
fi

# 최종 결과 로그
if [ "$SUCCESS" = true ]; then
    echo "[$(date +"%Y-%m-%d %H:%M:%S")] Scheduled indexing completed successfully" >> "$LOG_FILE"
    exit 0
else
    echo "[$(date +"%Y-%m-%d %H:%M:%S")] ERROR: Scheduled indexing completed with errors" >> "$LOG_FILE"
    exit 1
fi

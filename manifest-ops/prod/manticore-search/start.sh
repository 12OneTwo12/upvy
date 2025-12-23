#!/bin/bash
set -e

# cron 서비스 시작
service cron start

# 초기 인덱싱 실행 (content_index)
if [ ! -f /var/lib/manticore/content_index.sph ]; then
    echo "No existing content_index index found. Running initial indexing..."
    indexer content_index --config /etc/manticoresearch/manticore.conf 2>&1 | tee /var/log/manticore/initial_indexing_content.log

    if [ $? -eq 0 ]; then
        echo "Initial content_index indexing completed successfully"
    else
        echo "WARNING: Initial content_index indexing failed. Service will start without data."
    fi
else
    echo "Existing content_index index found. Skipping initial indexing."
fi

# 초기 인덱싱 실행 (user_index)
if [ ! -f /var/lib/manticore/user_index.sph ]; then
    echo "No existing user_index index found. Running initial indexing..."
    indexer user_index --config /etc/manticoresearch/manticore.conf 2>&1 | tee /var/log/manticore/initial_indexing_user.log

    if [ $? -eq 0 ]; then
        echo "Initial user_index indexing completed successfully"
    else
        echo "WARNING: Initial user_index indexing failed. Service will start without data."
    fi
else
    echo "Existing user_index index found. Skipping initial indexing."
fi

# 초기 인덱싱 실행 (autocomplete_index)
if [ ! -f /var/lib/manticore/autocomplete_index.sph ]; then
    echo "No existing autocomplete_index index found. Running initial indexing..."
    indexer autocomplete_index --config /etc/manticoresearch/manticore.conf 2>&1 | tee /var/log/manticore/initial_indexing_autocomplete.log

    if [ $? -eq 0 ]; then
        echo "Initial autocomplete_index indexing completed successfully"
    else
        echo "WARNING: Initial autocomplete_index indexing failed. Service will start without data."
    fi
else
    echo "Existing autocomplete_index index found. Skipping initial indexing."
fi

# 초기 인덱싱 실행 (tag_index)
if [ ! -f /var/lib/manticore/tag_index.sph ]; then
    echo "No existing tag_index index found. Running initial indexing..."
    indexer tag_index --config /etc/manticoresearch/manticore.conf 2>&1 | tee /var/log/manticore/initial_indexing_tag.log

    if [ $? -eq 0 ]; then
        echo "Initial tag_index indexing completed successfully"
    else
        echo "WARNING: Initial tag_index indexing failed. Service will start without data."
    fi
else
    echo "Existing tag_index index found. Skipping initial indexing."
fi

# Manticore를 포어그라운드로 실행
exec searchd --nodetach

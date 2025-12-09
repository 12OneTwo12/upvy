#!/bin/bash
set -e

# cron 서비스 시작
service cron start

# 초기 인덱싱 실행 (content_search)
if [ ! -f /var/lib/manticore/content_search.sph ]; then
    echo "No existing content_search index found. Running initial indexing..."
    indexer content_search --config /etc/manticoresearch/manticore.conf 2>&1 | tee /var/log/manticore/initial_indexing_content.log

    if [ $? -eq 0 ]; then
        echo "Initial content_search indexing completed successfully"
    else
        echo "WARNING: Initial content_search indexing failed. Service will start without data."
    fi
else
    echo "Existing content_search index found. Skipping initial indexing."
fi

# 초기 인덱싱 실행 (user_search)
if [ ! -f /var/lib/manticore/user_search.sph ]; then
    echo "No existing user_search index found. Running initial indexing..."
    indexer user_search --config /etc/manticoresearch/manticore.conf 2>&1 | tee /var/log/manticore/initial_indexing_user.log

    if [ $? -eq 0 ]; then
        echo "Initial user_search indexing completed successfully"
    else
        echo "WARNING: Initial user_search indexing failed. Service will start without data."
    fi
else
    echo "Existing user_search index found. Skipping initial indexing."
fi

# 초기 인덱싱 실행 (autocomplete_search)
if [ ! -f /var/lib/manticore/autocomplete_search.sph ]; then
    echo "No existing autocomplete_search index found. Running initial indexing..."
    indexer autocomplete_search --config /etc/manticoresearch/manticore.conf 2>&1 | tee /var/log/manticore/initial_indexing_autocomplete.log

    if [ $? -eq 0 ]; then
        echo "Initial autocomplete_search indexing completed successfully"
    else
        echo "WARNING: Initial autocomplete_search indexing failed. Service will start without data."
    fi
else
    echo "Existing autocomplete_search index found. Skipping initial indexing."
fi

# Manticore를 포어그라운드로 실행
exec searchd --nodetach

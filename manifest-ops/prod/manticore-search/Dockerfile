FROM manticoresearch/manticore:latest

# cron 설치
RUN apt-get update && apt-get install -y cron

# cron 작업 설정 (매시간 인덱싱)
RUN echo "0 * * * * /usr/bin/flock -n /var/run/manticore-indexer.lock /bin/bash -c 'echo \"[\$(date +\"%Y-%m-%d %H:%M:%S\")] Starting scheduled indexing...\" >> /var/log/manticore/cron.log && indexer content_search --config /etc/manticoresearch/manticore.conf --rotate >> /var/log/manticore/cron.log 2>&1 && indexer user_search --config /etc/manticoresearch/manticore.conf --rotate >> /var/log/manticore/cron.log 2>&1 && mysql -h 127.0.0.1 -P9306 -e \"RELOAD INDEXES;\" >> /var/log/manticore/cron.log 2>&1 && echo \"[\$(date +\"%Y-%m-%d %H:%M:%S\")] Scheduled indexing completed successfully\" >> /var/log/manticore/cron.log || echo \"[\$(date +\"%Y-%m-%d %H:%M:%S\")] ERROR: Scheduled indexing failed\" >> /var/log/manticore/cron.log'" > /etc/cron.d/manticore-indexer
RUN chmod 0644 /etc/cron.d/manticore-indexer
RUN crontab /etc/cron.d/manticore-indexer

# 시작 스크립트 복사
COPY start.sh /start.sh
RUN chmod +x /start.sh

CMD ["/start.sh"]

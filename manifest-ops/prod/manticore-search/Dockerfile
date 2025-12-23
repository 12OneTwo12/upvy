FROM manticoresearch/manticore:latest

# cron 설치
RUN apt-get update && apt-get install -y cron

# cron job 스크립트 복사
COPY cron-indexer.sh /usr/local/bin/cron-indexer.sh
RUN chmod +x /usr/local/bin/cron-indexer.sh

# cron 작업 설정 (매시간 인덱싱)
RUN echo "0 * * * * /usr/bin/flock -n /var/run/manticore-indexer.lock /usr/local/bin/cron-indexer.sh" > /etc/cron.d/manticore-indexer
RUN chmod 0644 /etc/cron.d/manticore-indexer
RUN crontab /etc/cron.d/manticore-indexer

# 시작 스크립트 복사
COPY start.sh /start.sh
RUN chmod +x /start.sh

CMD ["/start.sh"]

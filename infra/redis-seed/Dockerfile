FROM redis:8.2.2-alpine

# Só o redis-cli e o date do busybox são usados
COPY seed.sh /opt/nordstay/seed.sh
RUN sed -i 's/\r$//' /opt/nordstay/seed.sh

USER redis

ENTRYPOINT ["sh", "/opt/nordstay/seed.sh"]

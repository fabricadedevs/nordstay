FROM apache/kafka:4.3.1

# KRaft com um nó só: o mesmo processo é broker e controller
ENV KAFKA_NODE_ID=1 \
    KAFKA_PROCESS_ROLES=broker,controller \
    KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093 \
    KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
    # Dois "endereços": um para os containers, outro para a sua máquina
    KAFKA_LISTENERS=INTERNAL://:29092,EXTERNAL://:9092,CONTROLLER://:9093 \
    KAFKA_ADVERTISED_LISTENERS=INTERNAL://kafka:29092,EXTERNAL://localhost:9092 \
    KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=INTERNAL:PLAINTEXT,EXTERNAL:PLAINTEXT,CONTROLLER:PLAINTEXT \
    KAFKA_INTER_BROKER_LISTENER_NAME=INTERNAL \
    # Com 1 broker, o fator de replicação precisa ser 1
    KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
    KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1 \
    KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1 \
    KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS=0 \
    # Tópico só existe se o kafka-init criar
    KAFKA_AUTO_CREATE_TOPICS_ENABLE=false \
    KAFKA_LOG_DIRS=/var/lib/kafka/data \
    # Cabe numa máquina de 8 GB
    KAFKA_HEAP_OPTS="-Xms512m -Xmx512m"

# O volume nomeado herda o dono desta pasta
USER root
RUN mkdir -p /var/lib/kafka/data && chown -R appuser:appuser /var/lib/kafka
USER appuser

HEALTHCHECK --interval=10s --timeout=10s --start-period=30s --retries=10 \
  CMD /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:29092 > /dev/null 2>&1
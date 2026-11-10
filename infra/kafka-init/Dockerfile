FROM apache/kafka:4.3.1

COPY topics.txt create-topics.sh /opt/nordstay/

ENTRYPOINT ["bash", "/opt/nordstay/create-topics.sh"]
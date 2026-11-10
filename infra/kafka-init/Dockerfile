FROM apache/kafka:4.3.1

# A imagem roda como "appuser"; para ajustar arquivos copiados, precisamos de root
USER root

COPY topics.txt create-topics.sh /opt/nordstay/

# Remove \r (CRLF do Windows) caso algum arquivo escape do .gitattributes
RUN sed -i 's/\r$//' /opt/nordstay/* \
 && chmod 0755 /opt/nordstay/create-topics.sh

# Volta para o usuário sem privilégios para rodar o script
USER appuser

ENTRYPOINT ["bash", "/opt/nordstay/create-topics.sh"]

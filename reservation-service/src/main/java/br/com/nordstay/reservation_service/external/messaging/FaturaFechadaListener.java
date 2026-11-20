package br.com.nordstay.reservation_service.external.messaging;

import br.com.nordstay.reservation_service.application.port.in.ConcluirCheckoutUseCase;
import br.com.nordstay.reservation_service.domain.exception.EventoFaturaFechadaInvalidoException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class FaturaFechadaListener {

    private static final String TIPO_EVENTO = "nordstay.fatura-fechada";

    private final ObjectMapper objectMapper;
    private final ConcluirCheckoutUseCase concluirCheckout;

    public FaturaFechadaListener(
            ObjectMapper objectMapper,
            ConcluirCheckoutUseCase concluirCheckout) {
        this.objectMapper = objectMapper;
        this.concluirCheckout = concluirCheckout;
    }

    @KafkaListener(
            topics = "${reservation.fatura-fechada.topic:nordstay.fatura-fechada}",
            groupId = "${spring.kafka.consumer.group-id:reservation-service}"
    )
    public void receber(String payload) {
        JsonNode evento;
        try {
            evento = objectMapper.readTree(payload);
        } catch (JacksonException exception) {
            throw new EventoFaturaFechadaInvalidoException("JSON malformado", exception);
        }

        if (evento == null || !evento.isObject()) {
            throw new EventoFaturaFechadaInvalidoException("o envelope deve ser um objeto JSON");
        }
        JsonNode tipo = evento.get("tipo");
        if (tipo == null || !tipo.isTextual() || !TIPO_EVENTO.equals(tipo.textValue())) {
            throw new EventoFaturaFechadaInvalidoException("tipo não corresponde ao tópico");
        }
        JsonNode versao = evento.get("versao");
        if (versao == null || !versao.isIntegralNumber() || versao.intValue() != 1) {
            throw new EventoFaturaFechadaInvalidoException("versão não suportada");
        }
        JsonNode dados = evento.get("dados");
        if (dados == null || !dados.isObject()) {
            throw new EventoFaturaFechadaInvalidoException("campo dados é obrigatório");
        }
        JsonNode reservaId = dados.get("reservaId");
        if (reservaId == null || !reservaId.isTextual() || reservaId.textValue().isBlank()) {
            throw new EventoFaturaFechadaInvalidoException("dados.reservaId é obrigatório");
        }

        concluirCheckout.concluir(reservaId.textValue());
    }
}

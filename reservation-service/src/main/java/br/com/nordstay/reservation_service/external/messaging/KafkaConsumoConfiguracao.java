package br.com.nordstay.reservation_service.external.messaging;

import br.com.nordstay.reservation_service.domain.exception.EventoFaturaFechadaInvalidoException;
import br.com.nordstay.reservation_service.domain.exception.ReservaNaoEncontradaException;
import br.com.nordstay.reservation_service.domain.exception.TransicaoCheckoutInvalidaException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;

@Configuration(proxyBeanMethods = false)
@EnableKafka
public class KafkaConsumoConfiguracao {

    @Bean
    ObjectMapper objectMapper() {
        return JsonMapper.builder().build();
    }

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(
                        record.topic() + ".dlt",
                        record.partition()
                )
        );
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(1000);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(4000);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.addNotRetryableExceptions(
                EventoFaturaFechadaInvalidoException.class,
                ReservaNaoEncontradaException.class,
                TransicaoCheckoutInvalidaException.class
        );
        return errorHandler;
    }
}

package br.com.nordstay.reservation_service.application.service;

import br.com.nordstay.reservation_service.domain.service.PoliticaCheckin;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration(proxyBeanMethods = false)
public class RelogioConfiguracao {

    @Bean
    Clock clockCheckin() {
        return Clock.system(ZoneId.of("America/Recife"));
    }

    @Bean
    PoliticaCheckin politicaCheckin() {
        return new PoliticaCheckin();
    }
}

package br.com.nordstay.reservation_service.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;

@Configuration
@Profile("dev")
public class MockJwtConfig {

    @Bean
    public KeyPair mockKeyPair() throws Exception {
        KeyPairGenerator generator =
                KeyPairGenerator.getInstance("RSA");

        generator.initialize(2048);

        return generator.generateKeyPair();
    }

    @Bean
    public JwtDecoder jwtDecoder(KeyPair mockKeyPair) {

        return NimbusJwtDecoder
                .withPublicKey(
                        (RSAPublicKey) mockKeyPair.getPublic()
                )
                .build();
    }

    @Bean
    public JwtEncoder jwtEncoder(KeyPair mockKeyPair)
            throws Exception {

        RSAKey rsaKey = new RSAKey.Builder(
                (RSAPublicKey) mockKeyPair.getPublic()
        )
                .privateKey(mockKeyPair.getPrivate())
                .keyID("nordstay-lock-2026-11")
                .build();

        return new NimbusJwtEncoder(
                new ImmutableJWKSet<>(
                        new JWKSet(rsaKey)
                )
        );
    }
}
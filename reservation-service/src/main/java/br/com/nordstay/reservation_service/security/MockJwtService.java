package br.com.nordstay.reservation_service.security;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@Profile("dev")
public class MockJwtService {

    private final JwtEncoder jwtEncoder;

    public MockJwtService(JwtEncoder jwtEncoder) {
        this.jwtEncoder = jwtEncoder;
    }

    @PostConstruct
    void initToken() {
        String token = gerarToken(
                "RSV-20261115-0029"
        );

        System.out.println(token);
    }

    public String gerarToken(String reservaId) {

        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(reservaId)
                .issuedAt(now)
                .expiresAt(now.plus(1, ChronoUnit.HOURS))
                .id(UUID.randomUUID().toString())
                .build();

        JwsHeader header = JwsHeader
                .with(SignatureAlgorithm.RS256)
                .keyId("nordstay-lock-2026-11")
                .build();

        return jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();
    }
}
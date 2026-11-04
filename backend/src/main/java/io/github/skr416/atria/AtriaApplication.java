package io.github.skr416.atria;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

/**
 * Main entry point for Atria Backend (Modular Monolith).
 * Enforces UTC system timezone at startup in accordance with ADR-0002.
 */
@EnableScheduling
@SpringBootApplication
public class AtriaApplication {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(AtriaApplication.class, args);
    }

    @PostConstruct
    public void initUtcTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }
}

package io.github.skr416.atria.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Springdoc OpenAPI 3 specification metadata.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI atriaOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Atria Platform API")
                        .description("Attendance & Approval Workflow Platform — Modular Monolith API (zh-TW / en-US / ja-JP)")
                        .version("v0.1.0")
                        .contact(new Contact()
                                .name("SKR416")
                                .url("https://github.com/SKR416/atria"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")));
    }
}

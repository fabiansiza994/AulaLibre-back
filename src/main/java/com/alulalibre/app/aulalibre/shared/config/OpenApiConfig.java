package com.alulalibre.app.aulalibre.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI aulaLibreOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("AulaLibre API")
                        .description("API de consulta y solicitud de disponibilidad de salones universitarios")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                // Applied to every operation by default; POST /auth/login is the only public
                // endpoint and simply doesn't need the token Swagger would send anyway.
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}

package org.mfa.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(title = "MFA Backend API", version = "v1", description = "API for multi-factor authentication service")
)
public class OpenApiConfig {
}
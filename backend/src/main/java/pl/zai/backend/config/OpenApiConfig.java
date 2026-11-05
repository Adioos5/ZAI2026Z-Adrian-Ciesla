package pl.zai.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestAttribute;
import pl.zai.backend.auth.RequiresAdmin;

/**
 * Swagger UI at /swagger-ui.html, spec at /v3/api-docs. Endpoints marked {@link RequiresAdmin}
 * get the bearer scheme, so a token from /api/auth/login can be entered via "Authorize".
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    static {
        // The authenticated admin is injected by AdminAuthInterceptor, not sent by the client.
        SpringDocUtils.getConfig().addAnnotationsToIgnore(RequestAttribute.class);
    }

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info().title("ZAI 26Z backend API").version("2.0.0"))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer")));
    }

    @Bean
    public OperationCustomizer adminSecurityCustomizer() {
        return (operation, handlerMethod) -> {
            if (handlerMethod.hasMethodAnnotation(RequiresAdmin.class)) {
                operation.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
            }
            return operation;
        };
    }
}

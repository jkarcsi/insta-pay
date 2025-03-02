package com.kibitsolutions.instapay.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .openapi("3.0.3")
                .info(new Info()
                        .title("InstaPay API")
                        .version("1.0")
                        .contact(new Contact().name("Karoly Jugovits").email("jugovitskaroly@gmail.com"))
                        .description("API for instant payment transactions"));
    }
}

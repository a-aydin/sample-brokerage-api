package com.fintech.brokerage.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI customOpenAPI() {
		return new OpenAPI().info(new Info().title("Brokerage API").version("1.0")
				.description("Brokerage firm order management API documentation"));
	}
}

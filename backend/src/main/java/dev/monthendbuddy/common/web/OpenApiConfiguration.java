package dev.monthendbuddy.common.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.monthendbuddy.common.Money;

/**
 * The OpenAPI document (served in the {@code local} profile only), from which the frontend's types are generated
 * ({@code npm run gen:api}), so it must describe the wire format exactly.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

	static {
		// MoneyJacksonModule writes Money exactly like MoneyDto; without this the schema would show its accessors.
		SpringDocUtils.getConfig().replaceWithClass(Money.class, MoneyDto.class);
	}

	@Bean
	OpenAPI monthEndBuddyOpenApi() {
		return new OpenAPI()
			.info(new Info().title("MonthEndBuddy API")
				.version("v1")
				.description("Synthetic-data personal finance API. Money amounts are decimal strings."))
			.components(new Components().addSecuritySchemes("bearer",
					new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
			.addSecurityItem(new SecurityRequirement().addList("bearer"));
	}

}

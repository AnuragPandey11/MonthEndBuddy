package dev.monthendbuddy.identity.infrastructure;

import java.util.List;

import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import dev.monthendbuddy.identity.CurrentUser;

@Configuration(proxyBeanMethods = false)
class IdentityWebConfiguration implements WebMvcConfigurer {

	static {
		// CurrentUser comes from the token, never from the request, so keep it out of the OpenAPI document.
		SpringDocUtils.getConfig().addRequestWrapperToIgnore(CurrentUser.class);
	}

	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(new CurrentUserArgumentResolver());
	}

}

package com.aalsikke.controller;

import com.aalsikke.config.SecurityConfig;
import com.aalsikke.security.JwtAuthFilter;
import com.aalsikke.security.JwtAuthenticationEntryPoint;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

/**
 * KAN-335: switches on our real security rules for @WebMvcTest tests
 * (Spring Boot 4 web slices don't turn on Spring Security by themselves).
 */
@TestConfiguration
@EnableWebSecurity
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class})
class WebSecurityTestConfig {

    /**
     * JwtAuthFilter is a @Component, so Spring would also add it as a plain servlet filter.
     * In MockMvc that copy runs before the security chain, the chain then starts with an empty
     * context and the "once per request" guard skips the real one, so every token looked missing (401).
     * The filter must only run inside the security chain.
     */
    @Bean
    FilterRegistrationBean<JwtAuthFilter> jwtFilterOnlyInsideSecurityChain(JwtAuthFilter jwtAuthFilter) {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>(jwtAuthFilter);
        registration.setEnabled(false);
        return registration;
    }
}
package com.realdev.readle.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realdev.readle.global.exception.GlobalErrorCode;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

  @Bean
  public SecurityErrorResponseWriter securityErrorResponseWriter(ObjectMapper objectMapper) {
    return new SecurityErrorResponseWriter(objectMapper);
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, JwtService jwtService, SecurityErrorResponseWriter errorResponseWriter)
      throws Exception {
    CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    return http.csrf(
            csrf ->
                csrf.csrfTokenRepository(csrfRepository)
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .oauth2Login(AbstractHttpConfigurer::disable)
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(
                        (request, response, exception) ->
                            errorResponseWriter.write(response, GlobalErrorCode.UNAUTHORIZED))
                    .accessDeniedHandler(
                        (request, response, exception) ->
                            errorResponseWriter.write(response, GlobalErrorCode.FORBIDDEN)))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/api/auth/*/start",
                        "/api/auth/*/callback",
                        "/api/auth/refresh",
                        "/api/auth/session",
                        "/api/actuator/health",
                        "/health",
                        "/actuator/health")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(
            new JwtAuthenticationFilter(jwtService, errorResponseWriter),
            UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}

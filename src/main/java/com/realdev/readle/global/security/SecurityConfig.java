package com.realdev.readle.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realdev.readle.global.exception.GlobalErrorCode;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableConfigurationProperties({SecurityProperties.class, PrometheusMetricsProperties.class})
public class SecurityConfig {

  @Bean
  public SecurityErrorResponseWriter securityErrorResponseWriter(ObjectMapper objectMapper) {
    return new SecurityErrorResponseWriter(objectMapper);
  }

  @Bean
  @Order(1)
  public SecurityFilterChain authSecurityFilterChain(
      HttpSecurity http, JwtService jwtService, SecurityErrorResponseWriter errorResponseWriter)
      throws Exception {
    return configureCommon(http, jwtService, errorResponseWriter)
        .securityMatcher("/api/auth/**")
        .csrf(this::configureCookieCsrf)
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/api/auth/*/start",
                        "/api/auth/*/callback",
                        "/api/auth/refresh",
                        "/api/auth/session")
                    .permitAll()
                    .requestMatchers("/api/auth/logout")
                    .authenticated()
                    .anyRequest()
                    .authenticated())
        .build();
  }

  @Bean
  @Order(2)
  public SecurityFilterChain prometheusMetricsSecurityFilterChain(
      HttpSecurity http, PrometheusMetricsProperties properties) throws Exception {
    return http.securityMatcher("/api/actuator/prometheus")
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(Customizer.withDefaults())
        .authorizeHttpRequests(
            auth -> {
              if (properties.enabled()) {
                auth.anyRequest().hasRole("PROMETHEUS_METRICS");
              } else {
                auth.anyRequest().denyAll();
              }
            })
        .build();
  }

  @Bean
  public UserDetailsService prometheusMetricsUserDetailsService(
      PrometheusMetricsProperties properties) {
    return new InMemoryUserDetailsManager(
        User.withUsername(properties.username())
            .password("{bcrypt}" + new BCryptPasswordEncoder().encode(properties.password()))
            .roles("PROMETHEUS_METRICS")
            .build());
  }

  @Bean
  @Order(3)
  public SecurityFilterChain apiSecurityFilterChain(
      HttpSecurity http, JwtService jwtService, SecurityErrorResponseWriter errorResponseWriter)
      throws Exception {
    return configureCommon(http, jwtService, errorResponseWriter)
        .securityMatcher("/api/**")
        .csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/actuator/health/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .build();
  }

  @Bean
  @Order(4)
  public SecurityFilterChain fallbackSecurityFilterChain(
      HttpSecurity http, JwtService jwtService, SecurityErrorResponseWriter errorResponseWriter)
      throws Exception {
    return configureCommon(http, jwtService, errorResponseWriter)
        .securityMatcher("/**")
        .csrf(this::configureCookieCsrf)
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/health",
                        "/actuator/health/**",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/v3/api-docs.yaml")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .build();
  }

  private HttpSecurity configureCommon(
      HttpSecurity http, JwtService jwtService, SecurityErrorResponseWriter errorResponseWriter)
      throws Exception {
    return http.sessionManagement(
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
        .addFilterBefore(
            new JwtAuthenticationFilter(jwtService, errorResponseWriter),
            UsernamePasswordAuthenticationFilter.class);
  }

  private void configureCookieCsrf(CsrfConfigurer<HttpSecurity> csrf) {
    csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler());
  }
}

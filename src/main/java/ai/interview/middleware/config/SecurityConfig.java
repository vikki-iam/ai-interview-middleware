package ai.interview.middleware.config;

import ai.interview.middleware.security.JwtAuthenticationFilter;
import ai.interview.middleware.security.RestAccessDeniedHandler;
import ai.interview.middleware.security.RestAuthenticationEntryPoint;
import java.util.List;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * HTTP security.
 *
 * <p>Stateless by construction: no session is ever created, so scaling the Deployment needs no sticky
 * sessions and no shared session store. CSRF protection is disabled because there is no cookie-based
 * authentication for an attacker to ride; the bearer token has to be attached deliberately by the
 * client.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * BCrypt at strength 10.
     *
     * <p>Raising this is safe at any time: the cost factor is embedded in each stored hash, so
     * existing users keep verifying while new hashes use the stronger setting.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        // Always run the password comparison, even for an unknown email, so response time does not
        // reveal whether an account exists.
        provider.setHideUserNotFoundExceptions(true);
        return new ProviderManager(provider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler,
            CorsConfigurationSource corsConfigurationSource)
            throws Exception {

        http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .exceptionHandling(
                        handling ->
                                handling
                                        .authenticationEntryPoint(authenticationEntryPoint)
                                        .accessDeniedHandler(accessDeniedHandler))
                .headers(
                        headers ->
                                headers
                                        .frameOptions(frame -> frame.deny())
                                        .contentTypeOptions(options -> {})
                                        .httpStrictTransportSecurity(
                                                hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000))
                                        // This is a JSON API: nothing should ever be executed or embedded
                                        // from a response, and Swagger UI is served from the same origin.
                                        .contentSecurityPolicy(
                                                csp ->
                                                        csp.policyDirectives(
                                                                "default-src 'self'; img-src 'self' data:; "
                                                                        + "style-src 'self' 'unsafe-inline'; "
                                                                        + "script-src 'self'; frame-ancestors 'none'")))
                .authorizeHttpRequests(
                        registry ->
                                registry
                                        // Kubernetes probes and Prometheus scraping must work before any
                                        // credential exists. Everything else on /actuator stays protected.
                                        .requestMatchers(
                                                EndpointRequest.to("health", "info", "prometheus"))
                                        .permitAll()
                                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                                        .permitAll()
                                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh")
                                        .permitAll()
                                        .requestMatchers(
                                                "/v3/api-docs",
                                                "/v3/api-docs/**",
                                                "/swagger-ui.html",
                                                "/swagger-ui/**")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS driven entirely by {@code app.cors.*}, so the allowed origin is a Helm value rather than a
     * recompile. Wildcards are rejected by Spring when credentials are enabled, which is why
     * {@code allow-credentials} defaults to false: this API authenticates with a header, not a cookie.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
        AppProperties.Cors cors = properties.cors();
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(cors.allowedOrigins());
        configuration.setAllowedMethods(cors.allowedMethods());
        configuration.setAllowedHeaders(cors.allowedHeaders());
        configuration.setExposedHeaders(
                cors.exposedHeaders() == null ? List.of() : cors.exposedHeaders());
        configuration.setAllowCredentials(cors.allowCredentials());
        configuration.setMaxAge(cors.maxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}

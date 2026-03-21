package org.bruneel.thankyouboard.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;

import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        log.debug("Configuring security: secure board owner actions + list/create; anonymous board view/posts/giphy");

        RequestMatcher boardsListGet = req ->
                "GET".equals(req.getMethod()) &&
                        ("/api/boards".equals(req.getRequestURI()) || "/api/boards/".equals(req.getRequestURI()));
        RequestMatcher boardsCreatePost = req ->
                "POST".equals(req.getMethod()) &&
                        ("/api/boards".equals(req.getRequestURI()) || "/api/boards/".equals(req.getRequestURI()));

        Pattern boardByIdPattern = Pattern.compile("^/api/boards/[^/]+$");
        Pattern boardPdfPattern = Pattern.compile("^/api/boards/[^/]+/pdf$");
        Pattern boardPdfJobsPattern = Pattern.compile("^/api/boards/[^/]+/pdf-jobs(/.*)?$");
        Pattern postsListPattern = Pattern.compile("^/api/boards/[^/]+/posts/?$");
        Pattern postByIdPattern = Pattern.compile("^/api/boards/[^/]+/posts/[^/]+$");
        RequestMatcher boardByIdGet = req ->
                "GET".equals(req.getMethod()) && boardByIdPattern.matcher(req.getRequestURI()).matches();
        RequestMatcher boardPdfGet = req ->
                "GET".equals(req.getMethod()) && boardPdfPattern.matcher(req.getRequestURI()).matches();
        RequestMatcher boardPdfJobsAll = req ->
                boardPdfJobsPattern.matcher(req.getRequestURI()).matches();
        RequestMatcher boardByIdPut = req ->
                "PUT".equals(req.getMethod()) && boardByIdPattern.matcher(req.getRequestURI()).matches();
        RequestMatcher boardByIdDelete = req ->
                "DELETE".equals(req.getMethod()) && boardByIdPattern.matcher(req.getRequestURI()).matches();
        RequestMatcher postsListGet = req ->
                "GET".equals(req.getMethod()) && postsListPattern.matcher(req.getRequestURI()).matches();
        RequestMatcher postsCreatePost = req ->
                "POST".equals(req.getMethod()) && postsListPattern.matcher(req.getRequestURI()).matches();
        RequestMatcher postByIdPut = req ->
                "PUT".equals(req.getMethod()) && postByIdPattern.matcher(req.getRequestURI()).matches();
        RequestMatcher postByIdDelete = req ->
                "DELETE".equals(req.getMethod()) && postByIdPattern.matcher(req.getRequestURI()).matches();

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers((RequestMatcher) req -> "OPTIONS".equals(req.getMethod())).permitAll()
                        // Health check for load balancers, K8s probes, CI
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // Anonymous endpoints
                        .requestMatchers(boardByIdGet).permitAll()
                        .requestMatchers(postsListGet).permitAll()
                        .requestMatchers(postsCreatePost).permitAll()
                        .requestMatchers("/api/giphy/**").permitAll()
                        // Secured endpoints
                        .requestMatchers(boardsListGet).authenticated()
                        .requestMatchers(boardsCreatePost).authenticated()
                        .requestMatchers(boardPdfGet).authenticated()
                        .requestMatchers(boardPdfJobsAll).authenticated()
                        .requestMatchers(boardByIdPut).authenticated()
                        .requestMatchers(boardByIdDelete).authenticated()
                        // Post update/delete: allow anonymous requests so we can authorize via per-post capability tokens.
                        .requestMatchers(postByIdPut).permitAll()
                        .requestMatchers(postByIdDelete).permitAll()
                        // Everything else stays anonymous (frontend is separate)
                        .anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .cors(org.springframework.security.config.Customizer.withDefaults())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(org.springframework.security.config.Customizer.withDefaults()));

        return http.build();
    }

    @Bean
    @Profile("!test & !e2e")
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${auth0.audience}") String audience
    ) {
        NimbusJwtDecoder decoder = (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(issuerUri);
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> withAudience = new JwtAudienceValidator(audience);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience));
        return decoder;
    }

    @Bean
    @Profile({"test", "e2e"})
    public JwtDecoder testJwtDecoder(@Value("${auth0.audience:urn:test-api}") String audience) {
        return tokenValue -> Jwt.withTokenValue(tokenValue)
                .header("alg", "none")
                .issuer("https://test-issuer.example/")
                .audience(List.of(audience))
                .claim("sub", "test|user")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }
}

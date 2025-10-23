package org.mfa.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
public class SecurityConfig {

    @Value("${jwt.secret}") String jwtSecret;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {}) // <-- IMPORTANT: let Spring Security apply CORS

                .addFilterBefore(new JwtFilter(jwtSecret), AbstractPreAuthenticatedProcessingFilter.class)

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()   // preflight allowed
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers("/realms/**").permitAll()
                        .requestMatchers("/api/realms/**").permitAll()

                        // secure endpoints FIRST
                        .requestMatchers("/api/secure/**").authenticated()

                        // (Avoid a blanket /api/** permitAll, or put it AFTER the secure rule if you really need it)
                        .anyRequest().permitAll()
                );

        return http.build();
    }

    static class JwtFilter extends OncePerRequestFilter {
        private final byte[] key;
        JwtFilter(String secret) { this.key = secret.getBytes(StandardCharsets.UTF_8); }

        @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
                throws ServletException, IOException {
            String path = req.getRequestURI();
            if (!path.startsWith("/api/secure/")) { chain.doFilter(req, resp); return; }

            String auth = req.getHeader("Authorization");
            if (auth == null || !auth.startsWith("Bearer ")) { resp.setStatus(401); return; }

            String token = auth.substring(7);
            try {
                Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(key)).build().parseClaimsJws(token);
                chain.doFilter(req, resp);
            } catch (Exception e) {
                resp.setStatus(401);
            }
        }
    }
}

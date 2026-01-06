package org.raincityvoices.ttrack.service.config;

import org.raincityvoices.ttrack.service.auth.ClerkAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.azure.security.keyvault.secrets.SecretClient;
import com.clerk.backend_api.Clerk;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final SecretClient secretClient;

    @Bean
    public String clerkApiKey() {
        return secretClient.getSecret("ClerkAPIKeyTest").getValue();
    }

    @Bean
    public Clerk clerkSdk(String clerkApiKey) {
        log.info("ClerkAPIKey: {} chars", clerkApiKey.length());
        return Clerk.builder().bearerAuth(clerkApiKey).build();
    }

    @Bean ClerkAuthFilter clerkAuthFilter() { return new ClerkAuthFilter(); }
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> {}) // Must be before auth filter
            .csrf(csrf -> csrf.disable()) // Disable CSRF for API using Bearer tokens
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/public/**").permitAll() // Allow public access
                .anyRequest().authenticated() // Secure all other requests
                // .anyRequest().permitAll()
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // Use stateless sessions
            .addFilterBefore(clerkAuthFilter(), UsernamePasswordAuthenticationFilter.class) // Add your filter
            ;
        return http.build();
    }
}

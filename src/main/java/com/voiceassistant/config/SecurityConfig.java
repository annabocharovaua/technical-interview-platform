package com.voiceassistant.config;
import com.voiceassistant.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers("/api/auth/forgot-password", "/api/auth/reset-password").permitAll()
                        .requestMatchers("/api/auth/user/**", "/api/auth/trial/**").authenticated()
                        .requestMatchers("/api/auth/user/*/password").authenticated()
                        .requestMatchers("/api/debug/**").permitAll()
                        .requestMatchers("/api/feedback/generate").permitAll()
                        .requestMatchers("/api/feedback/pdf").permitAll()
                        .requestMatchers("/api/feedback/send-report").authenticated()
                        .requestMatchers("/api/feedback/rate-resource").authenticated()
                        .requestMatchers("/api/feedback/resources/**").permitAll()
                        .requestMatchers("/api/feedback/weak-questions").authenticated()
                        .requestMatchers("/api/coding/profile/**").authenticated()
                        .requestMatchers("/api/coding/recommend/**").authenticated()
                        .requestMatchers("/api/coding/progress").authenticated()
                        .requestMatchers("/api/coding/**").permitAll()
                        .requestMatchers("/api/realtime/**").authenticated()
                        .requestMatchers("/api/job/**").permitAll()
                        .requestMatchers("/api/payment/**").permitAll()
                        .requestMatchers("/api/resources/**").permitAll()
                        .requestMatchers("/api/weak-questions/**").permitAll()
                        .requestMatchers("/", "/home", "/login", "/profile", "/interview", "/avatar", "/results", "/live-coding", "/payment").permitAll()
                        .requestMatchers("/static/**").permitAll()
                        .requestMatchers("/*.html", "/*.js", "/*.css").permitAll()
                        .requestMatchers("/*.jpg", "/*.png", "/*.gif", "/*.ico", "/*.svg", "/*.webp").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/connect", "/disconnect").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
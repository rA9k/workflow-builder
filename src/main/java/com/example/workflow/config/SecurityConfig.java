package com.example.workflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.Customizer;

@Configuration
@EnableWebSecurity
public class SecurityConfig {


    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // http.authorizeRequests(requests -> requests
        //         // .requestMatchers("/workflow-builder").hasRole("user")
        //         .anyRequest().permitAll());
        
        http.csrf(csrf -> csrf.disable());
        // http.authorizeHttpRequests(req -> {
        //     // req.anyRequest().authenticated();
        // });
        // http.formLogin(Customizer.withDefaults());
        // http.oauth2Login(Customizer.withDefaults());
        return http.build();
    }
}
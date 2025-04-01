package com.example.workflow.config;

import java.util.Collection;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.config.Customizer;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Create a custom request matcher for Vaadin UIDL requests
        RequestMatcher uidlRequestMatcher = request -> request.getParameter("v-r") != null;

        // Configure CSRF with Vaadin support
        http.csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .ignoringRequestMatchers(
                        // Vaadin Flow internal requests
                        new AntPathRequestMatcher("/VAADIN/**"),
                        // Vaadin endpoint requests
                        new AntPathRequestMatcher("/connect/**"),
                        // Custom matcher for UIDL requests
                        uidlRequestMatcher));

        // Rest of your configuration...
        http.authorizeHttpRequests(req -> {
            req.requestMatchers("VAADIN/**", "/favicon.ico").permitAll();
            req.anyRequest().authenticated();
        });

        http.oauth2Login(Customizer.withDefaults());

        // Logout configuration.
        http.logout(logout -> logout
                .addLogoutHandler((request, response, authentication) -> request.getSession().invalidate())
                .logoutSuccessHandler((request, response, authentication) -> {
                    if (authentication != null && authentication.getPrincipal() instanceof OidcUser) {
                        OidcUser oidcUser = (OidcUser) authentication.getPrincipal();
                        // Check if the token is still valid
                        if (oidcUser.getIdToken().getExpiresAt().isAfter(java.time.Instant.now())) {
                            String idToken = oidcUser.getIdToken().getTokenValue();
                            String logoutUrl = "http://localhost:8080/realms/test/protocol/openid-connect/logout"
                                    + "?id_token_hint=" + idToken
                                    + "&post_logout_redirect_uri=http://localhost:8081";
                            response.sendRedirect(logoutUrl);
                        } else {
                            // If the token is expired, redirect without the id_token_hint
                            response.sendRedirect("http://localhost:8081");
                        }
                    } else {
                        response.sendRedirect("http://localhost:8081");
                    }
                }));

        return http.build();
    }

    @Bean
    public OidcUserService oidcUserService() {
        final OidcUserService delegate = new OidcUserService();
        return new OidcUserService() {
            @Override
            public OidcUser loadUser(OidcUserRequest userRequest) {
                OidcUser oidcUser = delegate.loadUser(userRequest);
                Map<String, Object> claims = oidcUser.getClaims();
                System.out.println("OIDC Claims: " + claims);

                Collection<SimpleGrantedAuthority> mappedAuthorities = new java.util.ArrayList<>();

                // Check for the realm_access claim
                Object realmAccessObj = claims.get("realm_access");
                if (realmAccessObj != null) {
                    if (realmAccessObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> realmAccess = (Map<String, Object>) realmAccessObj;
                        Object rolesObj = realmAccess.get("roles");
                        if (rolesObj != null) {
                            // Instead of checking for Collection, use Iterable:
                            if (rolesObj instanceof Iterable) {
                                for (Object roleObj : (Iterable<?>) rolesObj) {
                                    String role = String.valueOf(roleObj);
                                    mappedAuthorities.add(new SimpleGrantedAuthority(role));
                                }
                                System.out.println("Mapped roles from realm_access: " + mappedAuthorities);
                            } else {
                                System.out.println("roles is not an Iterable");
                            }
                        } else {
                            System.out.println("roles key not found in realm_access");
                        }
                    } else {
                        System.out.println("realm_access is not a Map");
                    }
                } else {
                    System.out.println("realm_access claim is not present");
                }

                // Return a new OidcUser with our mapped authorities (which may be empty if
                // mapping failed)
                return new DefaultOidcUser(mappedAuthorities, oidcUser.getIdToken(), oidcUser.getUserInfo());
            }
        };
    }
}

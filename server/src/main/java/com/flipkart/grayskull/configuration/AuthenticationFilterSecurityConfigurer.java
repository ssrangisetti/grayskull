package com.flipkart.grayskull.configuration;

import com.flipkart.grayskull.filters.AuthenticationFilter;
import com.flipkart.grayskull.spi.GrayskullAuthenticationProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.SecurityConfigurer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;

import java.io.IOException;

@AllArgsConstructor
public class AuthenticationFilterSecurityConfigurer implements SecurityConfigurer<DefaultSecurityFilterChain, HttpSecurity> {

    private final GrayskullAuthenticationProvider authenticationProvider;

    private static void entryPoint(HttpServletRequest req, HttpServletResponse resp, AuthenticationException auth) throws IOException {
        resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, auth.getMessage());
    }

    @Override
    public void init(HttpSecurity builder) {
        // Initialization not required for this configurer
    }

    @Override
    public void configure(HttpSecurity http) {
        AuthenticationManager authenticationManager = http.getSharedObject(AuthenticationManager.class);
        authenticationProvider.initialize(authenticationManager);
        http.addFilterAfter(new AuthenticationFilter(authenticationProvider, AuthenticationFilterSecurityConfigurer::entryPoint), SecurityContextHolderFilter.class);
    }
}

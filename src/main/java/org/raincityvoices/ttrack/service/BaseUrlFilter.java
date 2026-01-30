package org.raincityvoices.ttrack.service;

import java.io.IOException;
import java.util.Set;

import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import com.google.common.collect.ImmutableSet;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class BaseUrlFilter extends OncePerRequestFilter {

    private static final Set<String> CONTENT_TYPES = ImmutableSet.of(
        "application/json",
        "application/yaml",
        "text/html"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (CONTENT_TYPES.contains(response.getContentType())) {
        }
    }

}

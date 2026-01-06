package org.raincityvoices.ttrack.service.auth;

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.apache.commons.lang3.StringUtils;
import org.raincityvoices.ttrack.service.util.JsonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.filter.OncePerRequestFilter;

import com.clerk.backend_api.Clerk;
import com.clerk.backend_api.helpers.security.AuthenticateRequest;
import com.clerk.backend_api.helpers.security.VerifyToken;
import com.clerk.backend_api.helpers.security.models.AuthenticateRequestOptions;
import com.clerk.backend_api.helpers.security.models.RequestState;
import com.clerk.backend_api.helpers.security.models.TokenVerificationResponse;
import com.clerk.backend_api.helpers.security.models.VerifyTokenOptions;
import com.clerk.backend_api.models.components.User;
import com.clerk.backend_api.models.errors.ClerkErrors;
import com.clerk.backend_api.models.errors.VerifyOAuthAccessTokenOauthAccessTokensResponseBody;
import com.clerk.backend_api.models.operations.GetUserResponse;
import com.clerk.backend_api.models.operations.ListDomainsResponse;
import com.clerk.backend_api.models.operations.VerifyClientRequestBody;
import com.clerk.backend_api.models.operations.VerifyClientResponse;
import com.clerk.backend_api.models.operations.VerifyOAuthAccessTokenRequestBody;
import com.clerk.backend_api.models.operations.VerifyOAuthAccessTokenResponse;
import com.clerk.backend_api.operations.VerifyOAuthAccessToken;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClerkAuthFilter extends OncePerRequestFilter {

    @Autowired
    private String clerkApiKey;

    @Autowired
    private Clerk clerk;

    // TODO move this configuration to app properties or something
    private LoadingCache<String, User> userCache = CacheBuilder.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(60))
        .concurrencyLevel(5)
        .build(CacheLoader.from(this::fetchUser));

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        log.info("Authenticating request");
        RequestState reqState = AuthenticateRequest.authenticateRequest(
            getHeaders(request), 
            AuthenticateRequestOptions.secretKey(clerkApiKey)
            // TODO figure out what this should be
                                    // .authorizedParty("http://localhost:5173")
                                    .build());
        log.info("Request state: token='{}' status='{}' reason='{}' isAuth={} signedIn={}", 
            reqState.token(), reqState.status(), reqState.reason(), reqState.isAuthenticated(), reqState.isSignedIn());
        User user = null;
        if (reqState.isAuthenticated()) {
            if (reqState.claims().isEmpty()) {
                log.warn("No claims provided for authenticated user!");
                return;
            }
            Claims claims = reqState.claims().get();
            log.debug("All claims:");
            claims.forEach((name, value) -> { log.debug("- '{}': '{}'", name, value); });
            try {
                user = userCache.get(claims.getSubject());
            } catch (ExecutionException e) {
                log.error("Failed to fetch user info for authenticated user ID {}", claims.getSubject(), e);
            }
            log.debug("User: {}", user);
        }
        SecurityContext secCtx = SecurityContextHolder.createEmptyContext();
        secCtx.setAuthentication(new ClerkAuthentication(user));
        SecurityContextHolder.setContext(secCtx);
        filterChain.doFilter(request, response);
    }



    private User fetchUser(String userId) {
        try {
            GetUserResponse userResp = clerk.users().get(userId);
            return userResp.user().orElse(null);
        } catch (Exception e) {
            log.error("Failed to fetch user details from Clerk", e);
            return null;
        }
    }



    private Map<String, List<String>> getHeaders(HttpServletRequest request) {
        return Collections.list(request.getHeaderNames()).stream()
                .collect(toImmutableMap(n -> n, n -> Collections.list(request.getHeaders(n))));
    }
    
}

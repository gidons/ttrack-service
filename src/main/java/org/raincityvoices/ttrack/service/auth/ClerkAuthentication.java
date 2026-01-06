package org.raincityvoices.ttrack.service.auth;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import com.clerk.backend_api.models.components.User;

import lombok.ToString;

@ToString
public class ClerkAuthentication implements Authentication {

    private final User user;
    private final List<GrantedAuthority> authorities;

    public ClerkAuthentication(User user) {
        this.user = user;
        this.authorities = List.of(() -> "ADMIN");
    }

    @Override
    public String getName() {
        return user.id();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getDetails() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return isAuthenticated() ? new AuthenticatedPrincipal() {
            @Override
            public String getName() {
                return user.id();
            }
        } : null;
    }

    @Override
    public boolean isAuthenticated() {
        return user != null;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        throw new UnsupportedOperationException("Unimplemented method 'setAuthenticated'");
    }
    
}

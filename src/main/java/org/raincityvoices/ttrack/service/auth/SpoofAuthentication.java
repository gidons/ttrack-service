package org.raincityvoices.ttrack.service.auth;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

public class SpoofAuthentication implements Authentication, AuthenticatedPrincipal {
    
    String name;
    List<GrantedAuthority> authorities;

    public SpoofAuthentication(String name, String ... grantedAuthorities) {
        this.name = name;
        this.authorities = Stream.of(grantedAuthorities).map(a -> (GrantedAuthority) (() -> a)).toList();
    }

    @Override
    public String getName() { return name; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }

    @Override
    public Object getCredentials() { return null; }

    @Override
    public Object getDetails() { return null; }

    @Override
    public Object getPrincipal() { return isAuthenticated() ? this : null; }

    @Override
    public boolean isAuthenticated() { return name != null; }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        throw new UnsupportedOperationException("Unimplemented method 'setAuthenticated'");
    }
}

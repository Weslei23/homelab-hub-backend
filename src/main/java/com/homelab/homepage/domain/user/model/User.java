package com.homelab.homepage.domain.user.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.List;

@Entity
@Data
@NoArgsConstructor
@Table( name = "tb_users" )
public class User implements UserDetails
{
    @Id
    @GeneratedValue( strategy = GenerationType.IDENTITY )
    private Long id;
    private String email;
    private String username;
    private String password;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities()
    {
        return List.of();
    }

    @Override
    public boolean isAccountNonExpired()
    {
        return UserDetails.super.isAccountNonExpired();
    }

    @Override
    public boolean isAccountNonLocked()
    {
        return UserDetails.super.isAccountNonLocked();
    }

    @Override
    public boolean isCredentialsNonExpired()
    {
        return UserDetails.super.isCredentialsNonExpired();
    }

    @Override
    public boolean isEnabled()
    {
        return UserDetails.super.isEnabled();
    }
}
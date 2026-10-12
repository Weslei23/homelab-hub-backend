package com.homelab.homepage.domain.user.model;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class JWTUserData
{
    private Long userId;
    private String email;
    private Role role;
}

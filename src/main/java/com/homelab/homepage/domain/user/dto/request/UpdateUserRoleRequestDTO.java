package com.homelab.homepage.domain.user.dto.request;

import com.homelab.homepage.domain.user.model.Role;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequestDTO(
        @NotNull( message = "A role e obrigatoria (USER ou ADMIN)" )
        Role role )
{
}

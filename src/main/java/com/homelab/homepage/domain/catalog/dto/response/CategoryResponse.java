package com.homelab.homepage.domain.catalog.dto.response;

import java.util.List;

public record CategoryResponse( Long id, String label, List<CardResponse> services )
{
}

package com.homelab.homepage.dto;

import java.util.List;

public record CategoryResponse( Long id, String label, List<CardResponse> services )
{
}

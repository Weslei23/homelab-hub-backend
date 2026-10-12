package com.homelab.homepage.domain.user.model;

/**
 * Perfis de acesso.
 * USER: somente visualiza o dashboard (nao abre links nem edita).
 * ADMIN: acesso total, incluindo criar, editar e remover categorias e cards.
 */
public enum Role
{
    USER,
    ADMIN
}

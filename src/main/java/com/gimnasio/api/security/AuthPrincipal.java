package com.gimnasio.api.security;

/**
 * Principal autenticado extraído del JWT: identifica de forma unívoca a quién
 * pertenece la request (Usuario o Cliente, ambos comparten esta forma) sin
 * necesidad de volver a consultar la base para saber "quién soy".
 */
public record AuthPrincipal(Integer id, String sub, String rol) {
}

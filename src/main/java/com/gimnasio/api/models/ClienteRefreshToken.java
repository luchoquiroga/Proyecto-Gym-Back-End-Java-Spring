package com.gimnasio.api.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Registro en base de datos de un refresh token emitido a un Cliente autenticado
 * en el portal web. Separado de RefreshToken (el de Usuario/staff) a propósito:
 * son dos principales distintos, con ciclos de vida y controladores independientes.
 */
@Entity
@Table(name = "cliente_refresh_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClienteRefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String jti;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @Column(nullable = false)
    private Instant expiracion;

    @Column(nullable = false)
    private boolean revocado = false;
}

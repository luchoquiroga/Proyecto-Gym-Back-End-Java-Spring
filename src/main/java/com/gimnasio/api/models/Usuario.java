package com.gimnasio.api.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gimnasio.api.models.enums.RolUsuario;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "usuarios")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 100)
    private String nombre;

    // WRITE_ONLY: mismo motivo que Cliente.contrasena — POST /usuarios y PUT
    // /usuarios/cambiar-contrasena devuelven la entidad completa y filtrarían
    // el hash BCrypt al ADMIN que hizo la llamada.
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(nullable = false)
    private String contrasena;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RolUsuario rol;
}

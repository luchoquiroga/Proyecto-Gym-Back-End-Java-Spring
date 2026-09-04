package com.gimnasio.api.models;

import com.gimnasio.api.models.enums.EstadoCliente;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "clientes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 100)
    private String apellido;

    @Column(length = 50)
    private String telefono;

    /**
     * Email para el login del cliente en el portal web. Nulo hasta que el cliente
     * complete su registro (dado de alta primero por el staff sin credenciales).
     */
    @Column(unique = true, length = 150)
    private String email;

    /**
     * Contraseña hasheada (nunca en texto plano) del cliente para el portal web.
     */
    @Column(length = 255)
    private String contrasena;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20) default 'INACTIVO'")
    private EstadoCliente estado = EstadoCliente.INACTIVO;
}
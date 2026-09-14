package com.gimnasio.api.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    /**
     * Código de activación de un solo uso: se genera al dar de alta al cliente sin
     * credenciales y el staff se lo entrega en persona (o por teléfono). Es la prueba
     * de que quien completa /registro es realmente ese cliente, en vez de depender de
     * datos adivinables como nombre/apellido/teléfono. Se anula (vuelve a null) apenas
     * se usa, así que nunca sirve dos veces. @JsonIgnore porque nunca debe viajar en
     * una respuesta salvo la única vez que se genera (ver ClienteAltaResponse).
     */
    @JsonIgnore
    @Column(name = "codigo_activacion", unique = true, length = 10)
    private String codigoActivacion;
}
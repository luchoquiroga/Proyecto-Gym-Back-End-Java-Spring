package com.gimnasio.api.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Entity
@Table(name = "clientes")
@Data
@NoArgsConstructor
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 100)
    private String apellido;

    @Column(unique = true, nullable = false, length = 20)
    private String dni;

    @Column(length = 50)
    private String telefono;

    @Column(nullable = false)
    private LocalDate fechaInscripcion;

    @Column(nullable = false)
    private Boolean activo; // true si está al día, false si es moroso
}
package com.gimnasio.api.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "membresias")
@Data // Lombok: Autogenera getters, setters y toString
@NoArgsConstructor // Lombok: Constructor vacío requerido por JPA
public class Membresia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Autoincremental
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre; // ej: "Pase Libre Mensual"

    @Column(nullable = false)
    private Double precio;

    @Column(nullable = false)
    private Integer duracionDias; // ej: 30
}
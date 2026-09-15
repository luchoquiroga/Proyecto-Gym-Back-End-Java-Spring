package com.gimnasio.api.models.enums;

/**
 * Situación de un socio respecto de su cuota.
 *
 * Cada valor lleva una severidad explícita porque el vencimiento automático
 * (VencimientoServiceImpl) solo escala el estado y nunca lo revierte, y para eso necesita
 * comparar cuál de dos situaciones es "peor". Antes esa comparación usaba ordinal(), es
 * decir el orden en que están escritos los valores acá abajo: agregar un estado nuevo en
 * el medio (por ejemplo SUSPENDIDO entre ACTIVO y MOROSO) cambiaba en silencio la lógica
 * de vencimientos, sin que fallara ningún test ni el compilador. Con un número propio, el
 * orden de declaración deja de significar nada.
 *
 * El valor se persiste como texto (@Enumerated(EnumType.STRING) en Cliente), así que este
 * número no viaja a la base ni al API: es solo para comparar acá adentro.
 */
public enum EstadoCliente {

    ACTIVO(0),
    MOROSO(1),
    INACTIVO(2);

    private final int severidad;

    EstadoCliente(int severidad) {
        this.severidad = severidad;
    }

    /**
     * ¿Este estado es peor que el otro? Se usa para decidir si un vencimiento debe
     * escalar la situación del socio o dejarla como está.
     */
    public boolean esPeorQue(EstadoCliente otro) {
        return this.severidad > otro.severidad;
    }
}

package com.gimnasio.api.services;

import com.gimnasio.api.models.Plan;

import java.util.List;

/**
 * Interfaz que define las operaciones de negocio para el catálogo de Planes/Membresías.
 */
public interface PlanService {

    /**
     * Obtiene el listado completo de planes disponibles.
     */
    List<Plan> obtenerTodos();

    /**
     * Busca un plan por su ID único.
     * @param id Identificador del plan.
     * @return El plan encontrado.
     * @throws RuntimeException si no existe.
     */
    Plan obtenerPorId(Integer id);

    /**
     * Busca planes cuyo nombre contenga el texto dado, sin distinguir mayúsculas.
     * @param nombre Fragmento del nombre a buscar.
     * @return Los planes que coincidan; lista vacía si no hay ninguno. No lanza
     *         excepción: "no encontré nada" es un resultado válido de una búsqueda,
     *         no un error.
     */
    List<Plan> buscarPorNombre(String nombre);

    /**
     * Registra un nuevo plan en el catálogo.
     * @param plan Datos del nuevo plan (nombre, precio, duración en días).
     * @return El plan creado.
     */
    Plan crear(Plan plan);

    /**
     * Actualiza los datos de un plan existente (nombre, precio, duración).
     * @param id Identificador del plan.
     * @param planActualizado Nuevos datos del plan.
     * @return El plan actualizado.
     */
    Plan actualizar(Integer id, Plan planActualizado);

    /**
     * Elimina un plan si no tiene pagos históricos asociados.
     * @param id Identificador del plan a eliminar.
     */
    void eliminar(Integer id);
}

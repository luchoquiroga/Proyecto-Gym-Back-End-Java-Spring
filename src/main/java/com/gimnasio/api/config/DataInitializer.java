package com.gimnasio.api.config;

import com.gimnasio.api.models.Plan;
import com.gimnasio.api.models.Usuario;
import com.gimnasio.api.models.enums.RolUsuario;
import com.gimnasio.api.repositories.PlanRepository;
import com.gimnasio.api.repositories.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeder / Inicializador de datos iniciales.
 * Se ejecuta al iniciar la aplicación para garantizar que existan el usuario admin y los planes base.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private static final int LARGO_MINIMO_CONTRASENA_ADMIN = 12;

    private final UsuarioRepository usuarioRepository;
    private final PlanRepository planRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Contraseña del administrador inicial. No tiene valor por defecto a propósito: antes
     * acá había un "admin123" escrito en el código, o sea una credencial conocida por
     * cualquiera que viera el repositorio, lista para usarse en cualquier despliegue que
     * arrancara con la tabla vacía.
     *
     * Se valida solo cuando hay que sembrar (ver run): un despliegue sobre una base que ya
     * tiene usuarios no necesita esta variable y no tiene por qué fallar por ella.
     */
    @Value("${app.admin.contrasena-inicial:}")
    private String contrasenaAdminInicial;

    @Value("${app.admin.nombre-inicial:admin}")
    private String nombreAdminInicial;

    @Override
    public void run(String... args) {
        // 1. Inicializar usuario administrador si la tabla está vacía
        if (usuarioRepository.count() == 0) {
            if (contrasenaAdminInicial == null || contrasenaAdminInicial.isBlank()) {
                // Falla el arranque en vez de sembrar una credencial adivinable. Es el único
                // momento en que esta variable hace falta: con usuarios ya cargados, este
                // bloque ni se ejecuta.
                throw new IllegalStateException(
                        "La base no tiene ningún usuario y no hay contraseña inicial configurada. "
                                + "Definí la variable de entorno ADMIN_INITIAL_PASSWORD (mínimo 12 caracteres) "
                                + "antes del primer arranque contra una base vacía.");
            }
            if (contrasenaAdminInicial.length() < LARGO_MINIMO_CONTRASENA_ADMIN) {
                throw new IllegalStateException(
                        "La contraseña inicial del administrador es demasiado corta: mínimo "
                                + LARGO_MINIMO_CONTRASENA_ADMIN + " caracteres.");
            }

            Usuario admin = new Usuario(null, nombreAdminInicial,
                    passwordEncoder.encode(contrasenaAdminInicial), RolUsuario.ADMIN);
            usuarioRepository.save(admin);
            // Nunca loguear la contraseña, ni siquiera en el arranque: los logs de Render
            // quedan guardados y son visibles para cualquiera con acceso al panel.
            log.info(">> [DataInitializer] Usuario administrador inicial '{}' creado", nombreAdminInicial);
        }

        // 2. Inicializar catálogo de planes base si la tabla está vacía
        if (planRepository.count() == 0) {
            Plan paseMensual = new Plan(null, "Pase Mensual", 32500.0, 30);
            Plan paseDiario = new Plan(null, "Pase Diario / Clase", 1000.0, 1);

            planRepository.save(paseMensual);
            planRepository.save(paseDiario);
            log.info(">> [DataInitializer] Planes base ('Pase Mensual' y 'Pase Diario') cargados con éxito");
        }
    }
}

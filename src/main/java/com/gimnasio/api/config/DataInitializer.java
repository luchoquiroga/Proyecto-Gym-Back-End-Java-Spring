package com.gimnasio.api.config;

import com.gimnasio.api.models.Plan;
import com.gimnasio.api.models.Usuario;
import com.gimnasio.api.models.enums.RolUsuario;
import com.gimnasio.api.repositories.PlanRepository;
import com.gimnasio.api.repositories.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final UsuarioRepository usuarioRepository;
    private final PlanRepository planRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        // 1. Inicializar usuario administrador si la tabla está vacía
        if (usuarioRepository.count() == 0) {
            Usuario admin = new Usuario(null, "admin", passwordEncoder.encode("admin123"), RolUsuario.ADMIN);
            usuarioRepository.save(admin);
            log.info(">> [DataInitializer] Usuario 'admin' creado con éxito (Contraseña: admin123)");
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

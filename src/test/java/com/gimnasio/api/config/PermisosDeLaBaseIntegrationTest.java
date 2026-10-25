package com.gimnasio.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lo que puede y lo que no puede hacer el usuario con el que atiende la app (V10).
 *
 * La suite corre como dueño (ver test/resources/application.properties), así que nada más
 * se entera si a gimnasio_runtime le falta un permiso: lo vería recién producción, con un
 * 500. Este test es el que lo frena acá. Mira dos cosas: la tabla de permisos que la base
 * tiene registrada, y lo que pasa de verdad al conectarse con ese usuario.
 */
@SpringBootTest
class PermisosDeLaBaseIntegrationTest {

    private static final String USUARIO_DE_LA_APP = "gimnasio_runtime";

    /** Las únicas tablas en las que la app borra. Agregar una acá es una decisión, no un trámite. */
    private static final Set<String> TABLAS_CON_DELETE = Set.of("planes", "refresh_tokens", "cliente_refresh_tokens");

    /** "Permiso denegado" en Postgres. */
    private static final String SIN_PERMISO = "42501";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${spring.datasource.url}")
    private String url;
    @Value("${app.test.runtime.username}")
    private String usuario;
    @Value("${app.test.runtime.password}")
    private String contrasena;

    @Test
    void todasLasTablasDelGimnasio_seLeenEscribenYModifican_yBorranSoloLasQueCorresponde() {
        List<String> tablas = jdbcTemplate.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'",
                String.class);
        assertThat(tablas).contains("usuarios", "clientes", "planes", "pagos");

        for (String tabla : tablas) {
            for (String permiso : List.of("SELECT", "INSERT", "UPDATE")) {
                assertThat(tienePermiso(tabla, permiso)).as("%s sobre %s", permiso, tabla).isTrue();
            }
            assertThat(tienePermiso(tabla, "DELETE")).as("DELETE sobre %s", tabla)
                    .isEqualTo(TABLAS_CON_DELETE.contains(tabla));
            assertThat(tienePermiso(tabla, "TRUNCATE")).as("TRUNCATE sobre %s", tabla).isFalse();
        }
    }

    @Test
    void conectadoComoLaApp_leeYBorraPlanes_peroNoBorraLaCajaNiTocaElEsquema() throws SQLException {
        try (Connection conexion = DriverManager.getConnection(url, usuario, contrasena);
             Statement sql = conexion.createStatement()) {

            // Con RLS activo y sin políticas (V8), sin BYPASSRLS esto daría cero filas.
            assertThat(sql.executeQuery("SELECT count(*) FROM usuarios").next()).isTrue();

            // INSERT con IDENTITY sin permiso sobre la secuencia, y el DELETE que sí tiene.
            sql.executeUpdate("INSERT INTO planes (nombre, duracion, precio) VALUES ('permisos-v10', 1, 1)");
            assertThat(sql.executeUpdate("DELETE FROM planes WHERE nombre = 'permisos-v10'")).isEqualTo(1);

            assertSinPermiso(sql, "DELETE FROM pagos WHERE id = -1");
            assertSinPermiso(sql, "DELETE FROM clientes WHERE id = -1");
            assertSinPermiso(sql, "DELETE FROM usuarios WHERE id = -1");
            assertSinPermiso(sql, "TRUNCATE pagos");
            assertSinPermiso(sql, "DROP TABLE pagos");
            assertSinPermiso(sql, "ALTER TABLE pagos DROP CONSTRAINT ck_pagos_monto_positivo");
            assertSinPermiso(sql, "SELECT * FROM flyway_schema_history");
        }
    }

    private boolean tienePermiso(String tabla, String permiso) {
        // has_table_privilege con varios permisos juntos responde "alguno", no "todos":
        // por eso se pregunta de a uno.
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT has_table_privilege(?, 'public.' || ?, ?)", Boolean.class, USUARIO_DE_LA_APP, tabla, permiso));
    }

    private static void assertSinPermiso(Statement sql, String sentencia) {
        assertThatThrownBy(() -> sql.execute(sentencia))
                .as(sentencia)
                .isInstanceOf(SQLException.class)
                .extracting(e -> ((SQLException) e).getSQLState())
                .isEqualTo(SIN_PERMISO);
    }
}

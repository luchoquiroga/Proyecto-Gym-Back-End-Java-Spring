package com.gimnasio.api.services;

import com.gimnasio.api.models.Cliente;
import com.gimnasio.api.models.Pago;
import com.gimnasio.api.models.Plan;
import com.gimnasio.api.models.Usuario;
import com.gimnasio.api.models.enums.RolUsuario;
import com.gimnasio.api.repositories.ClienteRepository;
import com.gimnasio.api.repositories.PagoRepository;
import com.gimnasio.api.repositories.PlanRepository;
import com.gimnasio.api.repositories.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Dos operaciones sobre los mismos datos en transacciones simultáneas, contra la base real.
 *
 * <p>A diferencia del resto de los tests de integración, esta clase <b>no</b> es
 * {@code @Transactional}: la segunda operación corre en otro hilo y tiene que ver lo que la
 * primera confirma, así que los datos se commitean de verdad y cada test los borra al final,
 * por id.
 *
 * <p>El orden se fuerza, no se deja al azar: la primera operación hace su trabajo y deja la
 * transacción abierta; recién cuando la segunda terminó o quedó esperando un lock en la base
 * se deja confirmar a la primera. Así el test falla siempre que falte el bloqueo, y no una
 * vez cada tanto.
 */
@SpringBootTest
class ConcurrenciaIntegrationTest {

    @Autowired
    private PagoService pagoService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private PagoRepository pagoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private ExecutorService hilos;
    private TransactionTemplate transaccion;

    @BeforeEach
    void setUp() {
        hilos = Executors.newFixedThreadPool(2);
        transaccion = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void tearDown() {
        hilos.shutdownNow();
    }

    @Test
    @DisplayName("Dos anulaciones simultáneas del mismo pago: la segunda se rechaza y no pisa a la primera")
    void anular_dosVecesEnSimultaneo_laSegundaSeRechaza() throws Exception {
        Integer adminId = usuarioRepository.findByNombre("admin").orElseThrow().getId();
        Plan plan = planRepository.save(new Plan(null, "Plan test concurrencia", 1000.0, 30));
        Cliente cliente = clienteRepository.save(nuevoCliente());
        CountDownLatch soltarPrimera = new CountDownLatch(1);
        Future<?> primera = null;
        try {
            Pago pago = pagoService.registrarPago(cliente.getId(), plan.getId(), null, null, adminId);

            CountDownLatch primeraAnulo = new CountDownLatch(1);
            primera = hilos.submit(() -> transaccion.executeWithoutResult(estado -> {
                pagoService.anular(pago.getId(), "primera", adminId);
                primeraAnulo.countDown();
                esperar(soltarPrimera);
            }));
            assertTrue(primeraAnulo.await(10, TimeUnit.SECONDS), "La primera anulación no llegó a ejecutarse");

            Future<Pago> segunda = hilos.submit(() -> pagoService.anular(pago.getId(), "segunda", adminId));
            esperarBloqueadaOTerminada(segunda);
            soltarPrimera.countDown();
            primera.get(10, TimeUnit.SECONDS);

            ExecutionException error = assertThrows(ExecutionException.class, () -> segunda.get(10, TimeUnit.SECONDS));
            assertInstanceOf(IllegalArgumentException.class, error.getCause());
            assertTrue(error.getCause().getMessage().contains("ya estaba anulado"));
            // La auditoría es la de quien anuló primero: la segunda no la reescribió.
            assertEquals("primera", pagoRepository.findById(pago.getId()).orElseThrow().getMotivoAnulacion());
        } finally {
            soltarPrimera.countDown();
            esperarQueTermine(primera);
            pagoRepository.deleteAll(pagoRepository.findByClienteId(cliente.getId()));
            clienteRepository.deleteById(cliente.getId());
            planRepository.deleteById(plan.getId());
        }
    }

    @Test
    @DisplayName("Dos ADMIN que se dan de baja entre sí a la vez: la segunda baja se rechaza y queda uno activo")
    void cambiarActivo_bajaCruzadaEnSimultaneo_dejaUnAdminActivo() throws Exception {
        Usuario ana = usuarioRepository.save(new Usuario(null, "test-concurrencia-ana", "sin-uso", RolUsuario.ADMIN));
        Usuario beto = usuarioRepository.save(new Usuario(null, "test-concurrencia-beto", "sin-uso", RolUsuario.ADMIN));
        // La regla del último ADMIN solo entra en juego con exactamente dos activos, así que los
        // que ya existían (el admin sembrado) se apagan durante el test y se restauran por id.
        List<Integer> otrosAdmins = jdbcTemplate.queryForList(
                "SELECT id FROM usuarios WHERE rol = 'ADMIN' AND activo AND id NOT IN (?, ?)",
                Integer.class, ana.getId(), beto.getId());
        fijarActivo(otrosAdmins, false);
        CountDownLatch soltarPrimera = new CountDownLatch(1);
        Future<?> primera = null;
        try {
            CountDownLatch primeraDioDeBaja = new CountDownLatch(1);
            primera = hilos.submit(() -> transaccion.executeWithoutResult(estado -> {
                usuarioService.cambiarActivo(beto.getId(), false, ana.getId());
                primeraDioDeBaja.countDown();
                esperar(soltarPrimera);
            }));
            assertTrue(primeraDioDeBaja.await(10, TimeUnit.SECONDS), "La primera baja no llegó a ejecutarse");

            Future<Usuario> segunda = hilos.submit(() -> usuarioService.cambiarActivo(ana.getId(), false, beto.getId()));
            esperarBloqueadaOTerminada(segunda);
            soltarPrimera.countDown();
            primera.get(10, TimeUnit.SECONDS);

            ExecutionException error = assertThrows(ExecutionException.class, () -> segunda.get(10, TimeUnit.SECONDS));
            assertInstanceOf(IllegalArgumentException.class, error.getCause());
            assertTrue(error.getCause().getMessage().contains("último administrador"));
            assertTrue(usuarioRepository.findById(ana.getId()).orElseThrow().isActivo());
        } finally {
            soltarPrimera.countDown();
            esperarQueTermine(primera);
            fijarActivo(otrosAdmins, true);
            usuarioRepository.deleteAllById(List.of(ana.getId(), beto.getId()));
        }
    }

    private Cliente nuevoCliente() {
        Cliente cliente = new Cliente();
        cliente.setNombre("Test");
        cliente.setApellido("Concurrencia");
        // Aleatorio para no chocar con la restricción UNIQUE si quedó basura de una corrida cortada.
        cliente.setDocumento("TC" + ThreadLocalRandom.current().nextInt(10_000_000, 100_000_000));
        return cliente;
    }

    private void fijarActivo(List<Integer> ids, boolean activo) {
        ids.forEach(id -> jdbcTemplate.update("UPDATE usuarios SET activo = ? WHERE id = ?", activo, id));
    }

    /**
     * Espera a que la segunda operación termine o quede frenada por un lock de la base. Con
     * el bloqueo bien puesto queda frenada; sin él, termina sin esperar a nadie.
     */
    private void esperarBloqueadaOTerminada(Future<?> segunda) throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < limite) {
            if (segunda.isDone() || haySesionEsperandoUnLock()) {
                return;
            }
            Thread.sleep(20);
        }
        fail("La segunda operación no terminó ni quedó esperando un lock");
    }

    private boolean haySesionEsperandoUnLock() {
        Integer esperando = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() AND wait_event_type = 'Lock'",
                Integer.class);
        return esperando != null && esperando > 0;
    }

    private static void esperar(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Nadie soltó la primera operación");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void esperarQueTermine(Future<?> operacion) {
        if (operacion == null) {
            return;
        }
        try {
            operacion.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Solo importa que haya soltado su transacción antes de limpiar: su resultado ya
            // lo evaluó el test.
        }
    }
}

package com.gimnasio.api.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Envoltorio de un listado paginado.
 *
 * Existe en vez de devolver directamente el {@link Page} de Spring Data porque la
 * serialización JSON de `PageImpl` no es parte estable de su contrato (Spring avisa
 * de eso al arrancar): su forma puede cambiar entre versiones y arrastraría a las
 * tres apps que consumen este API. Acá la forma es nuestra y no se mueve sola.
 *
 * @param contenido      los elementos de esta página
 * @param pagina         número de página, empezando en 0
 * @param tamanio        cuántos elementos se pidieron por página
 * @param totalElementos total de elementos en todas las páginas
 * @param totalPaginas   cuántas páginas hay en total
 */
public record PaginaResponse<T>(
        List<T> contenido,
        int pagina,
        int tamanio,
        long totalElementos,
        int totalPaginas
) {

    /**
     * Construye la respuesta a partir de una página de entidades, aplicando `mapper`
     * a cada una para convertirla en su DTO. Que la conversión se haga acá evita que
     * cada controller repita el mismo armado.
     */
    public static <E, D> PaginaResponse<D> desde(Page<E> pagina, Function<E, D> mapper) {
        return new PaginaResponse<>(
                pagina.getContent().stream().map(mapper).toList(),
                pagina.getNumber(),
                pagina.getSize(),
                pagina.getTotalElements(),
                pagina.getTotalPages()
        );
    }
}

# AGENTS.md

## 1. Contexto del proyecto

Este proyecto es una aplicación desarrollada en Java.

El objetivo es mantener un código:
- claro;
- mantenible;
- modular;
- fácil de comprender para otro desarrollador.

La prioridad es mantener la calidad y coherencia del proyecto antes que implementar soluciones excesivamente complejas.

---

## 2. Reglas generales

- Antes de modificar código, analiza primero la implementación existente.
- Respeta la arquitectura actual del proyecto.
- No introduzcas cambios que no estén relacionados con la tarea solicitada.
- No modifiques archivos innecesarios.
- No elimines funcionalidades existentes sin autorización explícita.
- No introduzcas dependencias nuevas sin justificar su necesidad.
- Prioriza soluciones simples y mantenibles.
- Evita sobreingeniería.
- Evita duplicación de código.
- Utiliza nombres descriptivos para clases, métodos y variables.
- Mantén las responsabilidades de las clases claramente separadas.

---

## 3. Comportamiento del agente

Para tareas simples:

1. Analiza el código relevante.
2. Explica brevemente qué vas a cambiar.
3. Realiza el cambio.
4. Verifica que el código siga funcionando.

Para tareas complejas:

1. Analiza la arquitectura y los archivos involucrados.
2. Identifica dependencias y posibles impactos.
3. Explica el problema.
4. Propón un plan de implementación.
5. Espera confirmación antes de realizar cambios importantes.
6. Implementa la solución.
7. Ejecuta las verificaciones necesarias.
8. Resume los cambios realizados.

---

## 4. Seguridad de los cambios

Nunca:

- borres archivos sin autorización;
- modifiques el historial de Git;
- hagas `git push` automáticamente;
- hagas `git reset --hard` sin autorización;
- sobrescribas configuraciones importantes sin explicarlo;
- desactives tests para ocultar errores.

Antes de realizar operaciones potencialmente destructivas, solicita confirmación.

---

## 5. Java

- Utiliza las características de la versión de Java configurada por el proyecto.
- Respeta las convenciones estándar de Java.
- Favorece encapsulamiento y separación de responsabilidades.
- Evita métodos excesivamente largos.
- Evita clases con demasiadas responsabilidades.
- No utilices patrones de diseño únicamente por utilizarlos.
- Utiliza excepciones cuando representen realmente una condición excepcional.
- No utilices excepciones como mecanismo normal de control de flujo.

---

## 6. Arquitectura

Respeta la separación de responsabilidades existente.

Cuando corresponda:

- GUI / Controller → interacción con el usuario.
- Service → lógica de negocio.
- DAO / Repository → acceso a datos.
- Model / Entity → representación de datos.
- Util / Helper → funcionalidades realmente reutilizables.

No coloques lógica de negocio compleja en componentes de presentación o acceso a datos.

---

## 7. Base de datos

- No modifiques el esquema de la base de datos sin explicar el impacto.
- No elimines datos.
- No ejecutes operaciones destructivas sobre bases de datos reales.
- Utiliza consultas parametrizadas cuando corresponda.
- Mantén separada la lógica de acceso a datos de la lógica de negocio.

---

## 8. Testing

Después de realizar cambios relevantes:

- ejecuta los tests relacionados;
- verifica que el proyecto compile;
- informa cualquier test que falle.

Nunca elimines o desactives tests simplemente para conseguir una ejecución exitosa.

Si una funcionalidad importante no tiene tests, indícalo.

---

## 9. Git

- No realizar commits automáticamente.
- No realizar push automáticamente.
- No modificar el historial.
- No realizar operaciones destructivas sin autorización.

Antes de cambios importantes, se recomienda comprobar:

`git status`

---

## 10. Comunicación

No ocultes problemas encontrados durante el desarrollo.

Si detectas:

- un bug;
- una vulnerabilidad;
- una mala decisión arquitectónica;
- código duplicado;
- una dependencia innecesaria;
- una posible regresión;

indícalo claramente.

Si una solución propuesta por el usuario puede generar problemas, explica por qué y propone una alternativa.

---

## 11. Regla educativa

El usuario está aprendiendo Java.

Cuando exista una decisión técnica relevante:

- explica brevemente por qué se tomó;
- indica qué principio o concepto de programación está involucrado;
- evita ocultar completamente el razonamiento detrás de una implementación.

El objetivo no es solamente producir código funcional, sino ayudar al usuario a comprender y mantener el proyecto.
# PlantManager 1.3.1

Extensión de **G-Earth** para gestionar plantas monstruosas en **Habbo.es**.

Proyecto original de **Lanflyy**, recuperado y modificado por **martychisto** a partir de la versión 1.3.0. Esta publicación conserva el crédito original; no es una versión publicada por Lanflyy.

## Descargar e instalar

**[Descargar PlantManager 1.3.1 (.jar)](https://github.com/martychisto/PlantManager/releases/download/v1.3.1/PlantManager_1.3.1.jar)**

1. Descargá el `.jar` desde [Releases](https://github.com/martychisto/PlantManager/releases).
2. En G-Earth, entrá en **Extensiones → Instalar** y seleccioná ese archivo.
3. Activá Plants y entrá nuevamente a la sala para cargar las plantas y los muebles.
4. Si ya tenías otra versión, evitá ejecutar ambas al mismo tiempo.

No hace falta descargar el código `.java` ni los ZIP de código fuente que genera GitHub.

## Comandos

| Comando | Función |
| --- | --- |
| `:plants` | Trata las plantas vivas. |
| `:plants compost` | Consulta tus plantas y composta únicamente las confirmadas como muertas. |
| `:plants seed` | Planta tus semillas colocadas en el suelo de la sala. |
| `:plants canreproduce on` | Activa la reproducción. |
| `:plants canreproduce off` | Desactiva la reproducción. |
| `:plants abort` | Detiene el procesamiento. |

Las semillas deben estar en la sala, no en el inventario. Si el comando pide cargar el usuario, esperá un momento y repetilo.

## Cambios de esta versión

- Reconocimiento de semillas comunes, raras y no intercambiables de Habbo.es (tipos 4282, 4304 y 14088).
- Consulta del estado y propietario mediante `PetInfo` antes de compostar: no usa «se puede revivir» como equivalente de «está muerta».
- El contador de compostaje y semillas espera la retirada del objeto por el servidor; enviar un paquete no basta para contar un resultado.
- Conservación de las plantas cuando la lista de usuarios llega en varios lotes.
- Correcciones en la lectura de los muebles de la sala.

## Estado de las pruebas

- **Compostaje:** funcionamiento confirmado por martychisto dentro de Habbo.es.
- **Semillas:**  reconocimiento comprobado localmente; prueba dentro del hotel pendiente.
- **Pruebas locales:** 37 comprobaciones de lectura de paquetes, plantas vivas/muertas, propietarios y simulación de compostaje.

Los identificadores de semillas se verificaron en los [datos oficiales de Habbo.es](https://www.habbo.es/gamedata/furnidata_json/1). No se garantiza compatibilidad con otros hoteles.

## Código incluido

`src/main/java/` contiene las cinco clases modificadas, reconstruidas a partir del JAR 1.3.0 disponible. `tests/PatchTest.java` contiene las pruebas locales. **No es una recuperación completa del repositorio original ni de su historial.**

El JAR de la release conserva las clases y dependencias de la versión original y sustituye las clases corregidas. La identidad mostrada es `Lanflyy · modificado por martychisto`, versión `1.3.1`.

## Créditos y licencias

- Autor original: **Lanflyy**. Repositorio original: `Lanflyy/plant-manager` (actualmente no disponible).
- Recuperación y modificaciones: **martychisto**.
- Se conservan los avisos y licencias de dependencias incluidos en el JAR.
- No se recuperó el archivo de licencia específico del proyecto original; esta publicación no le asigna una licencia nueva ni atribuye a PlantManager las licencias de sus dependencias.

Proyecto independiente, sin afiliación con Habbo o Sulake.

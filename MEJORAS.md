# Mejoras sobre la versión de Gemini

Ejecutar con `run.bat` (igual que antes). El mundo antiguo (`level.dat`) se carga sin problemas y se guarda en el formato nuevo.

## Gráficos
- **Motor de iluminación real** (`level/LightEngine.java`): luz del cielo y luz de bloque de 0 a 15, propagada por BFS y actualizada de forma incremental al romper/colocar (≈10 µs por bloque). Las cuevas quedan a oscuras, las hojas dan sombra y el agua se oscurece con la profundidad.
- **Iluminación suave + oclusión ambiental** por vértice en todos los bloques.
- **Ciclo día/noche** de 10 minutos (`renderer/SkyRenderer.java`): sol, luna, estrellas, resplandor al amanecer/atardecer, cielo y niebla que cambian de color.
- **Nubes** que se desplazan con el viento.
- **Shader GLSL** (`renderer/WorldShader.java`): la luz del día se aplica en la GPU, sin reconstruir chunks.
- **Bloque en la mano** con animación al golpear/colocar, **balanceo de cámara** al andar y FOV más amplio al correr.
- Niebla roja y tinte al meter la cabeza en la lava.

## Bloques nuevos
Farol (emite luz), Vidrio, Ladrillos, Piedra pulida, Flor amarilla y Flor roja (aparecen en prados al generar el mundo). La lava ahora ilumina. Todos los nombres están en español.

## Jugabilidad
- **Inventario (E)** con todos los bloques (incluidas agua y lava) para personalizar la barra 1-9.
- **Correr (Ctrl)** y **modo vuelo (V)**: Espacio sube, Shift baja.
- Mantener el clic repite romper/colocar; el clic de la rueda copia el bloque a la barra.
- **T** adelanta el tiempo, **F3** depuración (coordenadas, luz, orientación), **F11** pantalla completa.
- **Pausa** con Esc (el mundo se detiene); Esc otra vez guarda y sale.
- **G** pide confirmación (pulsar dos veces) para no borrar el mundo por accidente.
- Mensajes emergentes en pantalla.

## Guardado
- Se guardan también posición, rotación, modo vuelo, hora del día y la barra rápida.
- **Autoguardado** cada 2 minutos en segundo plano, escribiendo a un archivo temporal para no corromper el mundo.

## Errores corregidos
- `Tesselator` no reiniciaba `len` en `begin()`: con el tiempo cada quad acababa en su propio `glDrawArrays` y el juego se ralentizaba.
- La lava se dibujaba dos veces (capa opaca y translúcida).
- La cola de ticks usaba `ArrayList.remove(0)` (O(n²)); ahora `ArrayDeque`.
- La fuente solo admitía ASCII y los glifos invadían a sus vecinos; ahora admite tildes y ñ.
- `setTileNoUpdate` (arena que cae, agua+lava → piedra) no actualizaba la luz.

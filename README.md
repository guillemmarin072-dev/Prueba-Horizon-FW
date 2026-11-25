# Builds de Horizon Forbidden West (Java, sin base de datos)

Web que recomienda **clase y build** de Horizon Forbidden West según la fase del juego que elijas.
Los datos salen de la guía *Best Builds Guide* de Game8 y viven en un archivo JSON dentro de la aplicación:

- 6 clases (Hunter, Warrior, Survivor, Machine Master, Infiltrator, Trapper) × 5 fases (inicio, mitad, final, post-juego, NG+ y DLC) = **30 builds**
- Catálogo de **162 objetos** (113 armas y 49 armaduras) con filtros por tipo, rareza y origen (juego base, Arena, New Game+, Burning Shores, reserva)
- La fase «NG+ y DLC» son builds propias hechas con mis stats; las otras cuatro salen de Game8
- Eliges fase y clase, y la página te lleva paso a paso a la build completa.

## Requisitos
JDK 17 o superior (`java -version` y `javac -version` deben mostrar 17+).

## Arrancar
```bash
./run.sh            # Linux / macOS     (Windows: run.bat)
```
Abre http://localhost:3000. Para otro puerto: `PORT=8080 ./run.sh` (en Windows: `set PORT=8080` antes de `run.bat`).

Los scripts compilan en la carpeta `out/` y arrancan. Si prefieres un único archivo:
```bash
mkdir out && javac --release 17 -d out $(find src/main/java -name '*.java')
cp -r src/main/resources/. out/
jar --create --file hfw-builds.jar --main-class com.hfw.builds.Main -C out .
java -jar hfw-builds.jar
```

## Pruebas
```bash
./test.sh           # Windows: test.bat
```
Comprueban el lector de JSON, la validación de los datos, el recomendador y la API completa (arrancan el servidor y lo consultan por HTTP).

## Estructura
```
run.sh / run.bat / test.sh / test.bat
src/main/java/com/hfw/builds/
  Main.java          servidor HTTP del JDK: API REST + archivos estáticos
  Catalog.java       carga hfw-data.json en memoria y valida todas las referencias
  Recommender.java   puntuación de clases
  Model.java         objetos de la API
  Json.java          lector y escritor JSON mínimo (sin librerías)
src/main/resources/
  hfw-data.json      todos los datos (builds, objetos, habilidades, Valor Surges)
  static/            index.html, styles.css, app.js
src/test/java/com/hfw/builds/SelfTest.java
```

## API
- `GET /api/meta` · fases, clases y estilos
- `GET /api/recommend?phase=late&styles=stealth,ranged` · clases ordenadas por encaje y su build para esa fase
- `GET /api/builds/{clase}/{fase}` · detalle de una build
- `GET /api/items?category=&rarity=&origin=&q=` · catálogo de equipo (filtros opcionales; `q` busca en el nombre)
- `GET /api/valor-surges` · los 12 Valor Surges con su nivel

Códigos: fases `early`, `mid`, `late`, `post`, `ngplus`; estilos `ranged`, `melee`, `stealth`, `traps`, `machines`, `defensive`;
clases `hunter`, `warrior`, `survivor`, `machine-master`, `infiltrator`, `trapper`.

## Modificar los datos
Edita `src/main/resources/hfw-data.json` y reinicia. Estructura:
- `items`: nombre → objeto con `category` (`Outfit` para armaduras), `rarity`, `origin` (`base`, `arena`, `ngplus`, `dlc`, `preorder`),
  `armorType` (solo armaduras), `howToGet`, y opcionalmente `statsLevel` (`max` = nivel 5, `base` = sin mejorar), `stats` (lista de líneas) y `note`.
  Al cargar se valida que rareza, origen y tipo de armadura sean válidos
- `skills`: habilidades de cada clase; `valorSurges`: nombre, clase, nivel (SS, S, A, B) y descripción
- `builds`: una por clase y fase, con `items`, `weaponMods`, `outfitMods`, `skills`, `follow` (habilidades a priorizar después),
  `skillNote` (opcional) y `valor` como lista de `[nombre, esPrincipal]`
- `classStyles`: afinidad 0-3 de cada clase con cada estilo. Es criterio propio, no de Game8.

Al arrancar se valida el archivo; si falta un objeto o una habilidad, el error dice cuál (por ejemplo
`hfw-data.json: late: objeto desconocido 'Nombre mal escrito'`). Ejecuta `./test.sh` después de editar.

## Notas
- La guía de Game8 es de marzo de 2022: no recoge cambios posteriores ni el DLC Burning Shores.
- Textos descriptivos reescritos en español; nombres de objetos, habilidades y mejoras en inglés.
- Los tipos de las armas legendarias (p. ej. *Forgefall* = Sharpshot Bow) se contrastaron con PowerPyx, porque la página de legendarios de Game8 no aparecian sus stats.

## Fondo con arte del artbook

La página es solo oscura. El fondo cambia con un fundido según la fase elegida, y la cabecera de cada build usa la ilustración de su clase. Las 13 imágenes WebP (~2,3 MB) están en `src/main/resources/static/img/`:

- Fases: `phase-early|mid|late|post|ngplus.webp`
- Clases: `class-hunter|warrior|survivor|machine-master|infiltrator|trapper.webp`
- Bandas: `band-surges.webp`, `band-gear.webp`

Para cambiar una imagen, sustituye el archivo por otro WebP con el mismo nombre. `SelfTest` comprueba que cada `img/*.webp` referenciado existe y se sirve como `image/webp`.

Las ilustraciones pertenecen a Guerrilla y Sony Interactive Entertainment y se usan solo con fines de fan/uso personal.

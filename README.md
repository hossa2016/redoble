# Prisma Widgets

Widget premium para la pantalla de inicio de Android, con fondos de pantalla animados. Funciona sobre el launcher original del teléfono (pensado para Xiaomi / HyperOS), así que se conservan los gestos de navegación.

## Qué trae
- **7 estilos:** Rock, Glaciar, Ámbar, Esmeralda, Claro, Neón y Lujo, cada uno con su propia letra.
- **Cabecera:** día, fecha y hora (con el reloj del sistema, siempre al día), clima de tu ciudad (Open-Meteo, sin clave) y batería.
- **Reproductor:** muestra lo que suena en tu app de música o en cualquier app, con controles en el mismo widget.
- **Accesos:** hasta 8 apps en 3 o 4 por fila, con nombre editable.
- **Cada parte se puede mostrar u ocultar,** y se elige el color de acento y el fondo del widget.
- **Fondos animados** y fondos fijos a partir de tus fotos (recorte, zoom y oscurecido).
- **Actualización en vivo** opcional: batería al instante y hora con la letra del tema (pide una notificación fija).

## Compilar
GitHub Actions compila el APK y el paquete para Play Store (.aab) en cada cambio a `main` y los publica en **Releases**. Los layouts del widget se generan con `python3 tools/layouts.py`.

## Privacidad
Ver [PRIVACY.md](PRIVACY.md).

## Fuentes
Anton, Barlow, Barlow Condensed, Cormorant, Inter, Inter Tight, Plus Jakarta Sans, Montserrat, Orbitron y Rajdhani, todas bajo SIL Open Font License 1.1, incluidas en `app/src/main/res/font`.

# Política de privacidad de Prisma Widgets

*Última actualización: 26 de noviembre de 2026*

Prisma Widgets es un widget para la pantalla de inicio de Android que muestra la hora, el clima, la batería, la música que suena y accesos a tus apps. Esta política explica qué datos usa la app y para qué.

## Resumen

- **Prisma Widgets no tiene cuentas, ni publicidad, ni analítica.**
- **No vendemos ni compartimos tus datos con nadie.**
- **Todo lo que configuras se guarda solo en tu teléfono.**

## Datos que usa la app

### Acceso a notificaciones (reproductor de música)
Si lo activas, Android le permite a la app ver las **sesiones de música activas**: título, artista, carátula y estado (sonando o en pausa), para mostrarlos en el widget con los botones ⏮ ⏯ ⏭.
- La app **no lee, no guarda y no envía el contenido de tus notificaciones ni de tus mensajes**.
- Si la app de música solo entrega la dirección de la carátula, Prisma Widgets la descarga directamente desde esa dirección para mostrarla.
- Al pedir una carátula, el servidor de imágenes ve la dirección IP del teléfono, como cualquier página web.
- Si la canción viene de YouTube y la app de música no entrega carátula, Prisma Widgets descarga la miniatura pública del video desde los servidores de imágenes de YouTube (i.ytimg.com), usando solo el identificador de la canción. No se envía nada más.
- Puedes quitar este permiso cuando quieras en *Ajustes → Acceso a notificaciones*.

### Ciudad para el clima
Si escribes tu ciudad, la app consulta el servicio público **Open-Meteo** (open-meteo.com) para obtener sus coordenadas y el pronóstico. Solo se envían el nombre de la ciudad y luego sus coordenadas, sin ningún dato que te identifique. **La app no usa el GPS ni tu ubicación.**

### Apps del widget
Para mostrar tus accesos directos, la app consulta la lista de apps instaladas en el teléfono. Esa lista no sale del teléfono.

### Fondo de pantalla y fotos
Si eliges una foto, la app la copia a su almacenamiento privado para crear el fondo. La foto no se sube a ningún lado.

### Colores de tu fondo (opcional)
Si activas «Color de tu fondo», la app calcula en el teléfono el color principal de tu fondo de pantalla para pintar los acentos. La imagen no se guarda ni se envía.

### Atajos para Tasker y Automate
La app acepta órdenes de otras apps de automatización solo para cambiar el escenario, el tema o el diseño, o para redibujar los widgets. Estas órdenes no leen ni envían datos.

### Fondos animados que se descargan
Algunos videos de fondo no vienen dentro de la app: se descargan la primera vez que los eliges, desde el repositorio público del proyecto en GitHub (raw.githubusercontent.com) o su copia en jsDelivr (cdn.jsdelivr.net). Solo se pide el archivo del video; como en cualquier descarga, esos servidores ven la dirección IP del teléfono. Los videos quedan guardados en la app.

### Conciertos cerca
La app muestra tus artistas más escuchados (del historial, que vive en tu teléfono). Al tocar uno, se abre una búsqueda web con el nombre del artista y tu ciudad. Si la versión incluye la búsqueda de Ticketmaster, la app le envía a Ticketmaster solo el nombre del artista y las coordenadas de la ciudad que escribiste para el clima.

### Batería
La app lee el nivel de batería y si está cargando, solo para mostrarlo en el widget.

### Agenda y alarma (opcional)
- **Próxima alarma:** la app lee la hora de tu próxima alarma desde Android para mostrarla en el widget. No pide permisos.
- **Próximo evento de la agenda:** solo si lo activas, la app pide permiso para leer tu calendario y muestra el título y la hora del próximo evento. Se lee en el teléfono y no se guarda ni se envía.
- **Widgets «Agenda» y «Pila inteligente»:** si los agregas y das el permiso, muestran los títulos y horas de tus eventos de hoy y mañana. Se leen en el teléfono y no se guardan ni se envían.
- **Regla «Concierto en tu agenda»:** si la activas, la app busca en los títulos de los eventos de hoy palabras como «concierto» o «festival» para cambiar el fondo. Nada sale del teléfono.

### Bitácora de Gira (opcional)
Si la activas, la app guarda **en tu teléfono** los títulos, artistas y minutos de lo que escuchas, para armar tu setlist semanal y el afiche del año. No sale del teléfono, salvo que tú compartas un afiche.

### Video propio (opcional)
Si eliges un video tuyo como fondo animado, se copia al almacenamiento privado de la app. No se sube a ningún lado.

### Copias de ajustes y diseños compartidos
- **Copia de ajustes:** se guarda donde tú elijas, en un archivo. Incluye tus ajustes, tu ciudad y la foto del fondo.
- **Compartir mi diseño:** genera un código (y una imagen con código QR) solo con el tema, colores, fondo animado elegido y diseño, **sin** ciudad, fotos, apps, música ni historial. Al pegar un código, la app lee el portapapeles solo para encontrarlo, y solo cambia esos ajustes de aspecto.
- Android también puede incluir los ajustes en su copia de seguridad del teléfono.

### Informe de errores
Si la app se cierra de forma inesperada, guarda **en tu teléfono** un registro técnico del error (modelo del teléfono, versión de Android y de la app). No se envía solo: tú decides si lo compartes desde *App → Enviar informe de errores*.

## Permisos
- **Internet:** consultar el clima y descargar carátulas.
- **Fondo de pantalla:** aplicar el fondo que elijas.
- **Notificaciones y servicio en primer plano** (opcional, "Actualización en vivo"): mantener la batería y la hora del widget al día. Se puede apagar en los ajustes.
- **Agenda** (opcional): mostrar el próximo evento si activas esa opción.
- **Iniciar al encender:** volver a programar las actualizaciones del widget después de reiniciar el teléfono.

## Niños
La app no está dirigida a menores de 13 años y no recoge datos personales de nadie.

## Cambios
Si esta política cambia, se actualizará en esta página con una nueva fecha.

## Contacto
Para dudas sobre privacidad, abre un *issue* en el repositorio del proyecto en GitHub.

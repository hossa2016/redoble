# Prisma Widgets · Paquete para publicar en Google Play

Lista de lo que hay que hacer en la consola de Google Play (no se puede hacer desde el código).

## 1. Clave de firma (antes de todo)
1. Crear una clave nueva (ver «Clave nueva» abajo) y guardarla **solo** en GitHub → Settings → Secrets and variables → Actions:
   `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
2. Activar **Play App Signing** al crear la app en la consola (Google guarda la clave final; la tuya es la de subida).
3. Sacar `app/redoble.keystore` y la contraseña de `app/build.gradle.kts` del repositorio.
4. Ojo: con una clave nueva, el APK ya instalado no se puede actualizar encima. Antes: *App → Guardar una copia de mis ajustes*, desinstalar, instalar el nuevo y *Restaurar una copia*.

## 2. Prueba cerrada (cuenta personal nueva)
- Se necesitan **12 probadores durante 14 días seguidos** antes de pasar a producción.
- Subir el `.aab` (GitHub lo genera junto al APK) a una prueba cerrada y compartir el enlace.

## 3. Servicio en primer plano (specialUse)
Texto sugerido para la declaración:
> «Actualización en vivo» es opcional y la activa el usuario. Mantiene al día la batería y la hora dibujada con la letra del tema en el widget de la pantalla de inicio. Si el sistema detiene el servicio, el widget vuelve al reloj del sistema y la batería se actualiza con las alarmas periódicas; nada se pierde.
- Grabar un video corto: abrir Prisma → Estilo → Tema → activar «Actualización en vivo» → ver la notificación fija y el widget actualizándose.
- Plan B si lo rechazan: quitar el servicio y dejar las actualizaciones periódicas que ya existen.

## 4. Seguridad de los datos (Data safety)
| Dato | ¿Se recoge? | ¿Se comparte? | Nota |
|---|---|---|---|
| Ubicación aproximada (ciudad escrita) | Se envía a Open-Meteo para el clima | No | No se guarda fuera del teléfono |
| Calendario (opcional) | No (solo se lee en el teléfono) | No | Permiso opcional |
| Música que suena (acceso a notificaciones) | No (solo se lee en el teléfono) | No | Carátulas de YouTube por id público |
| Historial de escucha (opcional) | No (solo en el teléfono) | No | Bitácora de Gira |
| Registros de errores | No (solo en el teléfono, el usuario decide compartir) | No | |
- Cifrado en tránsito: sí (HTTPS). Eliminar datos: desinstalar o «Borrar el informe».

## 5. Ficha
- Nombre: Prisma Widgets · Categoría: Personalización.
- Política de privacidad: enlace a `PRIVACY.md` del repositorio.
- Capturas: Completo, Hora y clima, Reproductor con piel Vinilo, Escenario Vivo, Mini 1×1, entrada de concierto.
- Idiomas de la ficha: español, inglés y portugués (la app ya está traducida).

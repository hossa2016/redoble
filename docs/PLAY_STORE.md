# Prisma Widgets · Paquete para publicar en Google Play

Lista de lo que hay que hacer en la consola de Google Play (no se puede hacer desde el código).

## 1. Clave de firma (antes de todo)
1. Crear una clave nueva (ver «Clave nueva» abajo) y guardarla **solo** en GitHub → Settings → Secrets and variables → Actions,
   con estos nombres exactos (son los que lee el flujo de compilación):
   `PRISMA_KEYSTORE_B64`, `PRISMA_KEYSTORE_PASSWORD`, `PRISMA_KEY_ALIAS`, `PRISMA_KEY_PASSWORD`.
   Mientras falten, la compilación muestra un aviso y firma con la clave vieja del repositorio.
2. Activar **Play App Signing** al crear la app en la consola (Google guarda la clave final; la tuya es la de subida).
3. Sacar `app/redoble.keystore` y la contraseña de `app/build.gradle.kts` del repositorio.
4. Ojo: con una clave nueva, el APK ya instalado no se puede actualizar encima. Antes: *App → Guardar una copia de mis ajustes*, desinstalar, instalar el nuevo y *Restaurar una copia*.

### Clave nueva
Sin computador, se puede crear con un flujo manual de GitHub Actions o en Termux:
```
keytool -genkeypair -v -keystore prisma.jks -keyalg RSA -keysize 4096 -validity 10000 -alias prisma
base64 -w0 prisma.jks > prisma.b64
```
El contenido de `prisma.b64` va en `PRISMA_KEYSTORE_B64`; la contraseña elegida en `PRISMA_KEYSTORE_PASSWORD` y `PRISMA_KEY_PASSWORD`; `prisma` en `PRISMA_KEY_ALIAS`. Guarda una copia de `prisma.jks` y la contraseña en un lugar seguro: sin ellas no podrás subir actualizaciones.

## 2. Prueba cerrada (cuenta personal nueva)
1. Crear la app en la consola y subir el `.aab` (GitHub lo genera junto al APK) a **Pruebas → Prueba cerrada**.
2. Crear la lista de probadores: una lista de correos o un Grupo de Google con **al menos 12 personas**.
3. Compartir el **enlace de participación** (opt-in) que da la consola; cada persona lo acepta y luego instala desde Play.
4. Los 12 deben seguir inscritos **14 días seguidos**; conviene pedirles que abran la app algunos días y dejen comentarios.
5. Al cumplirse, la consola habilita **Solicitar acceso a producción**, con un cuestionario sobre la prueba (cuántos probadores, qué cambió con sus comentarios).

## 3. Servicio en primer plano (specialUse)
Texto sugerido para la declaración:
> «Actualización en vivo» es opcional y la activa el usuario. Mantiene al día la batería y la hora dibujada con la letra del tema en el widget de la pantalla de inicio. Si el sistema detiene el servicio, el widget vuelve al reloj del sistema y la batería se actualiza con las alarmas periódicas; nada se pierde.
- Grabar un video corto: abrir Prisma → Estilo → Tema → activar «Actualización en vivo» → ver la notificación fija y el widget actualizándose.
- Plan B si lo rechazan: quitar el servicio y dejar las actualizaciones periódicas que ya existen.

## 4. Seguridad de los datos (Data safety)
| Dato | ¿Se recoge? | ¿Se comparte? | Nota |
|---|---|---|---|
| Ubicación aproximada (ciudad escrita) | **Sí**, procesada de forma efímera (se envía a Open-Meteo para el clima) | No | No se guarda fuera del teléfono |
| Calendario (opcional) | No (solo se lee en el teléfono) | No | Permiso opcional |
| Música que suena (acceso a notificaciones) | No (solo se lee en el teléfono) | No | Para la carátula se pide la imagen a la dirección que da la app de música o a i.ytimg.com (YouTube) con el id público de la canción; ese servidor ve la IP |
| Historial de escucha (opcional) | No (solo en el teléfono) | No | Bitácora de Gira |
| Registros de errores | No (solo en el teléfono, el usuario decide compartir) | No | |
- Portapapeles: al pegar un setup compartido, la app lee el texto copiado solo para buscar un código «PRISMA:».
- Cifrado en tránsito: sí (HTTPS). Eliminar datos: desinstalar o «Borrar el informe».

## 5. Ficha
- Nombre: Prisma Widgets · Categoría: Personalización.
- Política de privacidad: enlace a `PRIVACY.md` del repositorio.
- Capturas: Completo, Hora y clima, Reproductor con piel Vinilo, Escenario Vivo, Mini 1×1, entrada de concierto.
- Idiomas de la ficha: español, inglés y portugués (la app ya está traducida).

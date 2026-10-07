# Nebula

**Nebula v1.0** es un reproductor de música para Android centrado en reproducción local, personalización visual y radio online.

> Tu música, a tu manera.

## Funciones principales

- Reproducción de música local mediante MediaStore.
- Media3 / ExoPlayer con reproducción en segundo plano y MediaSession.
- Controles desde notificación, pantalla de bloqueo y Bluetooth.
- Cola de reproducción, aleatorio con historial, repetición y búsqueda dentro de la cola.
- Crossfade, gapless y normalización de volumen.
- Playlists y favoritos persistentes con Room.
- Biblioteca por canciones, álbumes, artistas y carpetas, con filtros configurables.
- Filtro opcional para ocultar audios de WhatsApp.
- Radio online enfocada en Argentina, con búsqueda y favoritas.
- Editor de metadatos y artwork.
- Reproductor altamente personalizable: skins 2D/3D, formas de portada, fondos, degradados, colores de artwork y barras de progreso configurables.
- Mini reproductor configurable.
- Selector de color HSV reutilizable en la personalización.
- Modo súper ahorro y optimizaciones de renderizado/batería.
- Juegos offline: Adivina la canción y El intruso.
- Pantalla inicial de permisos para biblioteca musical y notificaciones.

## Tecnología

- Java
- XML Views
- AndroidX / Material Components
- Media3 / ExoPlayer
- Room
- Retrofit / OkHttp / Gson
- Glide
- WorkManager
- minSdk 26
- targetSdk 35
- Java 17

## Abrir el proyecto

1. Cloná el repositorio.
2. Abrilo con Android Studio.
3. Usá JDK 17.
4. Permití que Android Studio sincronice Gradle.
5. Ejecutá el módulo `app` en un dispositivo Android 8.0 o superior.

El archivo `local.properties` se genera de forma local y no forma parte del repositorio.

## Permisos

Nebula solicita acceso a audio local y, en Android 13 o superior, permiso para notificaciones. La radio requiere conexión a Internet.

## Versión

Primera versión pública: **1.0**.

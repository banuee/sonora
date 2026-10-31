<div align="center">

# 🎵 Sonora

**Современный нативный аудиоплеер для Android в стиле Material 3 (Material You)**

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Media3](https://img.shields.io/badge/Media3-ExoPlayer-FF6F00?style=flat-square)](https://developer.android.com/media/media3)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg?style=flat-square)](LICENSE)

[Возможности](#-возможности) ·
[Установка](#-установка) ·
[Стек технологий](#-стек-технологий)

</div>

---

## ✨ Возможности

- 🎨 **Material 3 Expressive (Material You)**: Адаптивная цветовая схема под обои устройства (Dynamic Color), тональные поверхности, скругления и чистый лаконичный интерфейс.
- 🔍 **Поиск и стриминг (Hitmo)**: Встроенный нативный клиент Hitmo (`OkHttp` + `Jsoup`) для онлайн-прослушивания и скачивания треков в оригинальном качестве.
- 🖼 **Автоматический поиск обложек (iTunes Search API)**: Авто-замена заглушек треков на официальные HD-обложки (1000×1000 px) и вшивание их прямо в ID3v2-теги MP3.
- 📁 **Умный сканер папок**: Сканирование системной папки `Music/`, каталога `Download/` и пользовательских папок.
- 📑 **Плейлисты и M3U**: Создание плейлистов, выбор треков с поиском, быстрый импорт плейлистов формата M3U / M3U8 (включая бэкапы Musicolet).
- 🎚 **Скорость и тональность**: Регулировка скорости воспроизведения (0.25x – 2.0x) и высоты звука (pitch) для отдельного трека или для всех.
- 🎛 **Эквалайзер**: Интеграция с системным аудиопроцессингом и встроенный 5-полосный эквалайзер.
- 🔄 **OTA-обновления**: Автоматическая проверка и установка обновлений напрямую через GitHub Releases.
- 📦 **Резервное копирование**: Экспорт и восстановление настроек и плейлистов в один клик.

---

## 🛠 Стек технологий

- **Язык**: Kotlin
- **UI Framework**: Jetpack Compose (Material 3 BOM)
- **Аудиодвижок**: AndroidX Media3 (ExoPlayer + MediaLibraryService)
- **Сетевой слой**: OkHttp 4 + Jsoup
- **Теги MP3**: mp3agic (ID3v2.4)
- **Изображения**: Coil 3
- **Архитектура**: MVVM + Coroutines & StateFlow

---

## 🚀 Сборка и публикация

Для сборки и публикации релиза на GitHub:

```bash
chmod +x tools/release.sh
./tools/release.sh "Описание изменений релиза"
```

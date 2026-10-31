# AGENTS.md — Справочник для ИИ-агентов по проекту Sonora

Этот документ содержит ключевую информацию об архитектуре, зависимостях, интеграциях и нюансах кодовой базы **Sonora**, необходимую для продолжения разработки без потери контекста.

---

## 1. О проекте

- **Название:** Sonora
- **Назначение:** Локальный аудиоплеер для Android с Material 3 дизайном, парсером онлайн-каталога Hitmo (стриминг и загрузка в HD), авто-поиском и вшивкой HD-обложек через iTunes API, гибким управлением плейлистами (включая импорт M3U), эквалайзером, изменением скорости/высоты звука и встроенными OTA-обновлениями через GitHub Releases.
- **Путь к проекту:** `/home/banue/Проекты/sonora`
- **Репозиторий GitHub:** `https://github.com/banuee/sonora`
- **Пакет:** `com.sonora.player`

---

## 2. Стек технологий и окружение

- **ОС разработки:** Linux (Arch/btrfs).
- **Язык & UI:** Kotlin 2.1.10, Jetpack Compose, Material 3 Expressive, Navigation Compose.
- **Android Gradle Plugin (AGP):** `8.9.0` (или `9.2.1+`), Gradle `9.6.1`.
- **SDK:** `compileSdk = 35`, `targetSdk = 35`, `minSdk = 26`.
- **JDK:** OpenJDK 21 (`/usr/lib/jvm/java-21-openjdk`).
- **Gradle JVM параметры:** Заданы в `gradle.properties`:
  ```properties
  org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m
  ```
- **Keystore для релизов:**
  - Путь: `/home/banue/.config/metro-launcher/metro-release.keystore`
  - Конфигурация: вынесена в `local.properties` (не коммитится в git):
    ```properties
    RELEASE_STORE_FILE=/home/banue/.config/metro-launcher/metro-release.keystore
    RELEASE_STORE_PASSWORD=...
    RELEASE_KEY_ALIAS=metro
    RELEASE_KEY_PASSWORD=...
    ```
- **GitHub Token для публикации релизов:**
  - Путь: `/home/banue/.config/metro-launcher/github_token`

---

## 3. Архитектура и структура модулей

```
app/src/main/java/com/sonora/player/
├── SonoraApp.kt                     # Application-класс, инициализация служб
├── MainActivity.kt                  # Точка входа, BottomBar (5 вкладок), хост диалогов и BottomSheet плеера
├── data/
│   ├── model/
│   │   └── Models.kt                # Track, RemoteTrack, Playlist, EqualizerPreset, UpdateInfo
│   ├── remote/
│   │   ├── HitmoParser.kt           # Парсер каталога eu.hitmoz.com (JSON musmeta + Jsoup DOM)
│   │   ├── ItunesApi.kt             # Поиск HD обложек (1000x1000) через iTunes Search API
│   │   └── GithubUpdateChecker.kt   # Проверка и скачивание обновлений из GitHub Releases
│   ├── scanner/
│   │   └── AudioScanner.kt          # Сканер MediaStore + прямой обход /storage/emulated/0/Download и Music
│   ├── tagger/
│   │   └── Id3Tagger.kt             # Вшивка ID3v2 тегов и обложек в MP3 (mp3agic 0.9.1)
│   └── playlist/
│       ├── M3uParser.kt             # Парсер плейлистов M3U / M3U8 (включая бэкапы Musicolet)
│       └── PlaylistRepository.kt    # Хранилище плейлистов (JSON), экспорт/импорт бэкапов
├── service/
│   ├── SonoraMediaService.kt        # Media3 MediaLibraryService + ExoPlayer + Notification
│   ├── EqualizerManager.kt          # 5-полосный эквалайзер (android.media.audiofx.Equalizer)
│   └── DownloadManager.kt           # Загрузка MP3 в высоком качестве в Download/Sonora
└── ui/
    ├── components/
    │   ├── MiniPlayer.kt            # Мини-плеер над нижним баром
    │   ├── TrackListItem.kt         # Строка трека в списке
    │   ├── SpeedPitchDialog.kt      # Диалог скорости (0.25x-2x) и тональности (0.5x-1.5x)
    │   ├── EqualizerDialog.kt       # Диалог 5-полосного эквалайзера с тумблером
    │   ├── CreatePlaylistDialog.kt  # Создание плейлиста с мультивыбором треков
    │   ├── AddToPlaylistDialog.kt   # Добавление песни в плейлист
    │   └── CoverPickerSheet.kt      # Выбор вариантов обложек из iTunes
    ├── screens/
    │   ├── LibraryScreen.kt         # Экран локальной медиатеки
    │   ├── SearchScreen.kt          # Экран поиска по Hitmo со стримингом и загрузкой
    │   ├── PlaylistsScreen.kt       # Экран плейлистов + круглая кнопка FAB меню
    │   ├── PlayerBottomSheet.kt     # Полноэкранный плеер с 3-точечным меню действий
    │   └── SettingsScreen.kt        # Экран настроек (Звук, Бэкап, OTA-обновления, О плеере)
    ├── theme/
    │   ├── Color.kt                 # Material 3 цветовые токены
    │   └── Theme.kt                 # Material 3 Dynamic Color (Material You)
    └── viewmodel/
        └── PlayerViewModel.kt       # Центральный StateFlow ViewModel плеера
```

---

## 4. Критически важные детали реализации

### 4.1. Парсер Hitmo (`HitmoParser.kt`)
- **Проблема с транслитерацией:** Если брать название трека из ссылки скачивания, оно транслитерировано в латиницу (например, `Kukla_kolduna.mp3`).
- **Решение:** Данные извлекаются из HTML-атрибута `data-musmeta='{...}'` в JSON:
  - `artist` и `title` содержат оригинальные русские символы в UTF-8.
  - `url` содержит путь стриминга (`/get/cuts/...`).
  - Ссылка на скачивание полного 320kbps MP3 берется из `a.track__download-btn[href]` (`/get/music/...`).
- **Сетевые заголовки:** Для запросов к `eu.hitmoz.com` строго обязательны:
  - `User-Agent: Mozilla/5.0 ...`
  - `Referer: https://eu.hitmoz.com/`
  - `Accept-Language: ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7`
- *Примечание:* Сайт Hitmo может блокировать некоторые IP VPN датацентров (403). На мобильных устройствах без VPN работает стабильно.

### 4.2. Поиск обложек (`ItunesApi.kt`) и вшивка тегов (`Id3Tagger.kt`)
- iTunes Search API возвращает картинки `100x100bb.jpg`. URL программно заменяется на `1000x1000bb.jpg` для загрузки в HD.
- Библиотека `com.mpatric:mp3agic:0.9.1` (версия строго 0.9.1, так как 0.9.2 отсутствует в Maven Central).
- Используется класс `ID3v24Tag` (все заглавные буквы ID3).
- После сохранения временного файла и атомарной замены оригинала обязательно вызывается `MediaScannerConnection.scanFile`, чтобы MediaStore системы сразу подхватил обновленные теги и обложку.

### 4.3. Воспроизведение, скорость и высота звука
- Используется AndroidX Media3 (`1.5.1`).
- `ExoPlayer` в `SonoraMediaService.kt` настроен с кастомным `DefaultHttpDataSource.Factory`, чтобы передавать заголовки `Referer` и `User-Agent` при стриминге с Hitmo.
- Изменение скорости и высоты звука (Pitch) выполняется через `player.setPlaybackParameters(PlaybackParameters(speed, pitch))`.
- В `PlayerViewModel` реализован флаг `applySpeedPitchToAll`: при включении параметры запоминаются и применяются ко всем трекам, при выключении сбрасываются при смене песни.

### 4.4. Эквалайзер (`EqualizerManager.kt`)
- Использует нативный `android.media.audiofx.Equalizer`, привязанный к `exoPlayer.audioSessionId`.
- По умолчанию инициализируется 5 полос: 60 Гц, 230 Гц, 910 Гц, 3 кГц, 14 кГц.
- Поддерживается как открытие системного эквалайзера прошивки через Intent `AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL` (параметр `AudioEffect.EXTRA_AUDIO_SESSION`), так и встроенный графический Compose-диалог.

### 4.5. Плейлисты и M3U
- Локальные плейлисты сохраняются в JSON `sonora_playlists.json` в `context.filesDir`.
- Экспорт бэкапа поддерживает сохранение структуры в `Download/Sonora_Backup_<timestamp>.json`.
- `M3uParser.kt` сопоставляет пути `#EXTINF` и относительные ссылки (например, `Download/song.mp3`) с файлами из `AudioScanner`.

### 4.6. OTA-обновления и скрипт сборки
- Версия приложения задана в `app/build.gradle.kts` (`versionName = "1.0.0"`, `versionCode = 1`).
- Скрипт сборки и публикации: `tools/release.sh`:
  - Собирает релизный APK (`assembleRelease`).
  - Ставит git-тег `v<versionName>`.
  - Пушит тег на GitHub.
  - Создает GitHub Release с ченджлогом.
  - Загружает `app-release.apk` в ассеты релиза.
- В приложении `GithubUpdateChecker.kt` обращается к `https://api.github.com/repos/banuee/sonora/releases/latest`, сравнивает семантические версии, скачивает APK через OkHttp в кэш и открывает диалог установки через FileProvider (`application/vnd.android.package-archive`).

---

## 5. UI и правила микротекста (Clean UI Guidelines)

Проект строго следует правилу минималистичного текста без слопа и маркетинговых прикрас:
1. **Никаких обязательных подзаголовков** под кнопками или плитками списка (запрещено: `title: "Блюр", subtitle: "Мягкий акриловый фрост для читаемости"`).
2. **Никаких глаголов-инструкций** в лейблах («Нажмите чтобы...», «Кликните для...»). Только функциональные глаголы/существительные: «Скачать», «Сохранить», «Сброс», «Готово».
3. **Никаких инструкций по горячим клавишам** в виде предложений.
4. **Никаких рекламных прилагательных** («без лагов», «умный алгоритм»).
5. **Никаких технических утечек** путей в подписи кнопок (пути отображаются только в соответствующих полях или диалогах).

---

## 6. Основные команды для работы

```bash
# Переход в директорию проекта
cd /home/banue/Проекты/sonora

# Сборка debug версии
./gradlew assembleDebug

# Сборка release версии
./gradlew assembleRelease

# Запуск юнит-тестов
./gradlew test

# Установка на подключенный эмулятор / телефон
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Запуск главной активности плеера
adb shell am start -n com.sonora.player.debug/com.sonora.player.MainActivity

# Публикация нового релиза на GitHub (автосборка, тег, загрузка APK)
./tools/release.sh "Описание изменений релиза"
```

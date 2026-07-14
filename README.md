# Мультиокно (navisplit) — кастомный мультиокно-лаунчер для авто-бокса

Приложение открывает пары приложений двумя freeform-окнами бок о бок («сплит») по
настраиваемым сценариям. Плитки сценариев можно вынести на рабочий стол виджетами.

## Устройство (боевой бокс)

| Параметр | Значение |
|---|---|
| Модель/платформа | `trinket` — Qualcomm SM6125 (Snapdragon 665-класс) |
| Android | 13 (API 33), строка release=14 фейковая; сборка `eng.lynx`, `release-keys` |
| Экран | 1920 × 716, landscape |
| Root | нет; **загрузчик уже разблокирован** (verifiedbootstate=orange, flash.locked=0) |
| Стоковый лаунчер | `com.android.launcher3` (привилегированный Quickstep, /system_ext/priv-app), отключён |
| Текущий HOME | Nova (`com.teslacoilsw.launcher`) через LauncherHijack |
| adb | по Wi-Fi, порт 5555 (бокс + этот Mac должны быть в одной сети/на одном хотспоте) |

## Ключевые выводы

- **Классический split (разделитель) с кастомным HOME без root невозможен** — Overview/split-кнопка
  привязаны к Quickstep-как-HOME.
- **Freeform-мультиокно работает без root** и не зависит от HOME. Включается один раз через adb:
  ```
  adb shell settings put global enable_freeform_support 1
  adb shell settings put global force_resizable_activities 1
  adb reboot            # обязательно; переживает ребут, сбрасывается только factory reset/OTA
  ```
- Обычное приложение может открывать окна в freeform (проверено: Taskbar это делает).
- **Hidden-API `setLaunchWindowingMode`** заблокирован; расшивка через `VMRuntime.setHiddenApiExemptions`
  на этой прошивке **запатчена**. Обход, который применён здесь: `targetSdkVersion=25` в манифесте —
  для низкого target'а скрытый max-target метод доступен напрямую.

## Структура

```
freeform/
  app/
    AndroidManifest.xml                 targetSdk=25 (важно для freeform), queries, виджет/активности
    src/com/artman/navisplit/
      MainActivity.java                 UI: список сценариев, редактор (выбор приложений + раскладка)
      Scenario.java                     модель: id, name, left, right, leftPercent
      Store.java                        сохранение сценариев + маппинг виджет→сценарий (SharedPreferences/JSON)
      Launcher.java                     запуск пары в freeform с границами (+ фикс высоты под навбар)
      LaunchActivity.java               прозрачный триггер (дёргается виджетом)
      WidgetProvider.java               виджет-плитка на рабочий стол
      WidgetConfigActivity.java         выбор сценария при размещении виджета
    res/                                widget layout, tile bg, appwidget-provider
  toolchain/                            JDK 17 + build-tools 34 + android.jar(33) + adb  (НЕ в git — см. fetch-toolchain.sh)
  fetch-toolchain.sh                    качает toolchain (JDK+SDK) в ./toolchain, точные пиннутые версии
  build.sh                              самодостаточная сборка (при первом запуске сама зовёт fetch-toolchain.sh)
  navisplit.apk                         последняя собранная сборка
  screenshots/                          скриншоты прогресса с бокса
```

## Сборка

```
bash build.sh
```
Выход: `navisplit.apk` в корне.

**Первый запуск** скачивает тулчейн (JDK 17 + Android SDK, ~660 МБ) в `./toolchain`
через `fetch-toolchain.sh` (нужен интернет и macOS). Дальше тулчейн локальный, сборка офлайн.
Скачать отдельно: `bash fetch-toolchain.sh`.

## Установка / тест на боксе

```
ADB=toolchain/platform-tools/adb
$ADB connect <IP-бокса>:5555          # напр. 10.160.57.163:5555
$ADB install -r navisplit.apk
```
На боксе: открыть «Мультиокно» → тап по сценарию = сплит. Плитки: добавить виджет «Мультиокно»
на рабочий стол Nova → выбрать сценарий.

## Статус и открытые задачи

- ✅ UI сценариев (создание/редактирование/удаление, выбор приложений, раскладки 50/50, 65/35, 35/65, 60/40, 40/60)
- ✅ Виджеты-плитки на рабочий стол (по сценарию)
- ✅ Фикс высоты окон — не заходят под нижнюю панель ◁○□
- ✅ Обход hidden-API через targetSdk=25
- ⏳ **НЕ протестировано на боксе:** сборка с targetSdk=25 (freeform-фикс) — предыдущая (targetSdk=33)
  открывала окна fullscreen стопкой. Проверить, что теперь реально тайлит.
- ⏳ **Переезд уже открытых окон при смене профиля.** Если WM игнорирует bounds для существующей задачи —
  обычным приложением чужие задачи не подвинуть (нельзя их перечислить без привилегий).
  Бронебойный фолбэк — **root**: `adb shell am task resize <taskId> <l> <t> <r> <b>`.

## Запасной вариант

Taskbar (`com.farmerbb.taskbar`) поставлен и настроен на боксе (freeform-режим вкл.,
`WRITE_SECURE_SETTINGS` + usage-access выданы) — рабочий, но UX неудобный; это приложение его заменяет.

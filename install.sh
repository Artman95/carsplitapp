#!/bin/bash
# Установка «Мультиокна» на бокс одной командой: ищет бокс в сети, ставит APK, выдаёт право
# WRITE_SECURE_SETTINGS и включает freeform.
#
#   ./install.sh                 найти бокс в сети (порт 5555) и поставить
#   ./install.sh 10.0.0.5        указать адрес бокса
#   ./install.sh --reboot        сразу перезагрузить бокс, если нужен freeform
#   ./install.sh --apk файл.apk  поставить другой APK (по умолчанию ./navisplit.apk)
#
# Условие: на боксе включена «Отладка по Wi-Fi», компьютер и бокс в одной сети.
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
PKG=com.artman.navisplit
APK="$DIR/navisplit.apk"
TARGET=""
REBOOT=0

die() { echo "Ошибка: $*" >&2; exit 1; }

while [ $# -gt 0 ]; do
  case "$1" in
    --reboot) REBOOT=1 ;;
    --apk) APK="$2"; shift ;;
    -h|--help) sed -n '2,10p' "$0"; exit 0 ;;
    *) TARGET="$1" ;;
  esac
  shift
done

[ -f "$APK" ] || die "нет файла $APK. Соберите (bash build.sh) или скачайте APK из релиза: https://github.com/Artman95/carsplitapp/releases/latest"

# --- adb: свой, из тулчейна, из PATH или скачанный platform-tools (~12 МБ, без JDK/SDK) ---
find_adb() {
  if [ -n "$ADB" ] && [ -x "$ADB" ]; then return; fi
  for c in "$DIR/toolchain/platform-tools/adb" "$DIR/.platform-tools/platform-tools/adb"; do
    if [ -x "$c" ]; then ADB="$c"; return; fi
  done
  if command -v adb >/dev/null 2>&1; then ADB="$(command -v adb)"; return; fi
  case "$(uname -s)" in
    Darwin) os=darwin ;;
    Linux) os=linux ;;
    *) die "нет adb. Установите Android platform-tools и повторите" ;;
  esac
  echo "adb не найден, скачиваю platform-tools (~12 МБ)…"
  mkdir -p "$DIR/.platform-tools"
  curl -fL -o "$DIR/.platform-tools/pt.zip" "https://dl.google.com/android/repository/platform-tools-latest-$os.zip"
  unzip -q -o "$DIR/.platform-tools/pt.zip" -d "$DIR/.platform-tools"
  ADB="$DIR/.platform-tools/platform-tools/adb"
}

# --- поиск бокса: хосты /24 с открытым портом 5555 ---
find_box() {
  local ip base me
  if [ "$(uname -s)" = Darwin ]; then
    me="$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || true)"
  else
    me="$(hostname -I 2>/dev/null | awk '{print $1}')"
  fi
  [ -n "$me" ] || die "не удалось определить IP компьютера. Укажите адрес бокса: ./install.sh <IP>"
  base="${me%.*}"
  echo "Ищу бокс с открытым портом 5555 в $base.0/24…" >&2
  for i in $(seq 1 254); do
    ip="$base.$i"
    [ "$ip" = "$me" ] && continue
    ( nc -z -w 1 "$ip" 5555 >/dev/null 2>&1 && echo "$ip" ) &
  done
  wait
}

find_adb

if [ -z "$TARGET" ]; then
  found="$(find_box)"
  [ -n "$found" ] || die "бокс не найден. Проверьте: бокс включён, «Отладка по Wi-Fi» включена, вы в одной сети. Или укажите адрес: ./install.sh <IP>"
  TARGET="$(echo "$found" | head -1)"
  [ "$(echo "$found" | wc -l)" -gt 1 ] && echo "Найдено несколько устройств ($(echo $found)), беру $TARGET. Другой адрес можно указать аргументом."
fi
case "$TARGET" in *:*) ;; *) TARGET="$TARGET:5555" ;; esac

echo "Подключаюсь к $TARGET…"
"$ADB" connect "$TARGET" >/dev/null
a() { "$ADB" -s "$TARGET" "$@"; }

state=""
for _ in $(seq 1 15); do
  state="$(a get-state 2>&1 | tr -d '\r' || true)"
  [ "$state" = device ] && break
  [ "$state" = unauthorized ] && echo "Подтвердите отладку на экране бокса («Всегда разрешать»)…"
  sleep 2
done
[ "$state" = device ] || die "бокс не подключился (состояние: $state). Повторите: adb kill-server, затем ./install.sh"

echo "Устанавливаю $(basename "$APK")…"
a install -r "$APK" | tail -1

echo "Выдаю право WRITE_SECURE_SETTINGS (автовключение отладки после перезагрузки)…"
a shell pm grant "$PKG" android.permission.WRITE_SECURE_SETTINGS

changed=0
for key in enable_freeform_support force_resizable_activities; do
  if [ "$(a shell settings get global "$key" | tr -d '\r')" != 1 ]; then
    a shell settings put global "$key" 1
    changed=1
  fi
done

a shell am start -n "$PKG/.MainActivity" >/dev/null

echo "Готово. На боксе открыто «Мультиокно»: создайте сценарии и добавьте виджеты."
if [ "$changed" = 1 ]; then
  if [ "$REBOOT" = 1 ]; then
    echo "Freeform включён, перезагружаю бокс (отладка вернётся сама примерно через минуту)…"
    a reboot
  else
    echo "Freeform включён только что: перезагрузите бокс, иначе окна не разделятся."
    echo "  Автоматически: ./install.sh --reboot   или   $ADB -s $TARGET reboot"
  fi
fi

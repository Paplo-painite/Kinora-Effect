#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
APP_ICON="$ROOT/assets/kinora-icon.png"

usage() {
  cat <<'USAGE'
Build Kinora Effect for Android.

Usage:
  ./build.sh [options]

Options:
  -appicon=<path>  Use a different square PNG launcher icon
  -h, --help       Show this help

The app name and Android package are fixed to Kinora Effect / com.kinora.effect.
USAGE
}

for arg in "$@"; do
  case "$arg" in
    -h|--help) usage; exit 0 ;;
    -appicon=*) APP_ICON="${arg#-appicon=}" ;;
    *) echo "Unsupported option: $arg" >&2; usage >&2; exit 2 ;;
  esac
done

[[ -f "$APP_ICON" ]] || { echo "Icon not found: $APP_ICON" >&2; exit 2; }
exec bash "$ROOT/android-builder/build.sh" \
  -effectcraft \
  -appname="Kinora Effect" \
  -packagename="com.kinora.effect" \
  -appicon="$APP_ICON"

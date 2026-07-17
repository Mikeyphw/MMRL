#!/system/bin/sh
# Manual fail-boot trigger file watchdog.
# If fail_boot_file_path exists, AshReXcue marks the boot failed once for that
# exact file fingerprint. The same unchanged file is ignored on later boots.

manual_fail_setting_bool() {
  case "$(get_prop "$1" 2>/dev/null)" in
    false|0|no|off) return 1 ;;
    *) return 0 ;;
  esac
}

manual_fail_setting_int() {
  local key="$1" default="$2" min="$3" max="$4" value
  value="$(get_prop "$key" 2>/dev/null)"
  case "$value" in ''|*[!0-9]*) value="$default" ;; esac
  [ "$value" -lt "$min" ] 2>/dev/null && value="$min"
  [ "$value" -gt "$max" ] 2>/dev/null && value="$max"
  printf '%s' "$value"
}

manual_fail_seen_file() {
  if [ -n "${ASH_STATE_DIR:-}" ]; then
    printf '%s/manual_fail_file.seen' "$ASH_STATE_DIR"
  elif [ -n "${PERSIST_DIR:-}" ]; then
    printf '%s/manual_fail_file.seen' "$PERSIST_DIR"
  else
    printf '%s' "/data/adb/ashlooper/manual_fail_file.seen"
  fi
}

manual_fail_file_fingerprint() {
  local path="$1" stat_line size mtime hash

  stat_line="$(stat -c '%s:%Y:%i' "$path" 2>/dev/null || true)"
  [ -n "$stat_line" ] || stat_line="$(ls -ln "$path" 2>/dev/null | awk '{print $5 ":" $6 ":" $7 ":" $8}')"

  if command -v sha256sum >/dev/null 2>&1; then
    hash="$(sha256sum "$path" 2>/dev/null | awk '{print $1}')"
  elif command -v toybox >/dev/null 2>&1 && toybox sha256sum "$path" >/dev/null 2>&1; then
    hash="$(toybox sha256sum "$path" 2>/dev/null | awk '{print $1}')"
  else
    hash="nohash"
  fi

  printf '%s|%s|%s' "$path" "$stat_line" "$hash"
}

wait_for_manual_fail_file_storage() {
  local wait="$1" path="$2" elapsed=0

  while [ "$elapsed" -lt "$wait" ]; do
    [ -e "$path" ] && return 0

    # /sdcard may mount after early boot on some ROMs.
    if [ -d /sdcard ] || [ -d /storage/emulated/0 ]; then
      return 1
    fi

    sleep 1
    elapsed=$((elapsed + 1))
  done

  return 1
}

check_manual_fail_file_once() {
  manual_fail_setting_bool fail_boot_file_required || return 0

  local path wait seen file_sig old_sig dir

  path="$(get_prop fail_boot_file_path 2>/dev/null)"
  [ -n "$path" ] || path="/sdcard/failboot"

  case "$path" in
    /sdcard/*|/storage/emulated/0/*) ;;
    *)
      log "Manual fail trigger ignored: unsafe path outside shared storage: $path"
      return 0
      ;;
  esac

  wait="$(manual_fail_setting_int fail_boot_file_wait 20 0 120)"
  if [ ! -e "$path" ] && [ "$wait" -gt 0 ]; then
    wait_for_manual_fail_file_storage "$wait" "$path" || true
  fi

  [ -e "$path" ] || return 0

  file_sig="$(manual_fail_file_fingerprint "$path")"
  seen="$(manual_fail_seen_file)"
  old_sig="$(cat "$seen" 2>/dev/null || true)"

  if [ "$file_sig" = "$old_sig" ]; then
    log "Manual fail trigger file already consumed; ignoring unchanged file: $path"
    return 0
  fi

  dir="$(dirname "$seen")"
  mkdir -p "$dir" 2>/dev/null || true
  printf '%s\n' "$file_sig" > "$seen.tmp" 2>/dev/null && mv -f "$seen.tmp" "$seen" 2>/dev/null || true
  chmod 0600 "$seen" 2>/dev/null || true

  trigger_crash_reboot "manual fail trigger file exists at $path"
}

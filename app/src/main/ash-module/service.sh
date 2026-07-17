#!/system/bin/sh

MODPATH="${0%/*}"
mkdir -p /data/adb/ashlooper/logs
. "$MODPATH/utils.sh" 2>>/data/adb/ashlooper/logs/looperbug.log || exit 1
[ -f "$MODPATH/manual_fail_file.sh" ] && . "$MODPATH/manual_fail_file.sh"

validate_settings
set_log_file
if instance_lock_acquire service; then
  log "Service instance lock acquired (PID=$$)."
else
  log "Warning duplicate service instance detected (PID=$$). Exiting."
  exit 0
fi
cleanup_lock() { instance_lock_release; }
trap cleanup_lock EXIT INT TERM

CHECK_CMD=""
validate_tools() {
  if command -v pidof >/dev/null 2>&1 && pidof init 2>/dev/null | tr ' ' '\n' | grep -q '^1$'; then CHECK_CMD=pidof; return 0; fi
  CHECK_CMD=proc
  [ -d /proc/1 ]
}

check_process() {
  local proc_name="$1" pid cmd
  if [ "$CHECK_CMD" = pidof ]; then pidof "$proc_name" >/dev/null 2>&1 && return 0; fi
  for pid in /proc/[0-9]*; do
    [ -r "$pid/cmdline" ] || continue
    cmd=$(tr '\000' ' ' < "$pid/cmdline" 2>/dev/null)
    case "$cmd" in "$proc_name"|"$proc_name "*|*"/$proc_name"|*"/$proc_name "*) return 0 ;; esac
  done
  return 1
}

first_pid_of() {
  local proc_name="$1" result pid cmd
  if command -v pidof >/dev/null 2>&1; then result=$(pidof "$proc_name" 2>/dev/null); set -- $result; [ -n "$1" ] && { echo "$1"; return; }; fi
  for pid in /proc/[0-9]*; do
    [ -r "$pid/cmdline" ] || continue
    cmd=$(tr '\000' ' ' < "$pid/cmdline" 2>/dev/null)
    case "$cmd" in "$proc_name"|"$proc_name "*|*"/$proc_name"|*"/$proc_name "*) echo "${pid##*/}"; return ;; esac
  done
}

if ! validate_tools; then trigger_crash_reboot "process inspection unavailable"; fi
log "Executing service.sh with process checker: $CHECK_CMD"

BUILD_CHANGED=0
BUILD_GRACE=0
BUILD_GRACE_REASON=""
current_build=$(current_build_identity)
previous_build=$(cat "$BUILD_STATE" 2>/dev/null)
if [ -z "$previous_build" ]; then
  BUILD_CHANGED=1; BUILD_GRACE="$first_boot_grace"; BUILD_GRACE_REASON="first stable boot"
elif [ "$current_build" != "$previous_build" ]; then
  BUILD_CHANGED=1; BUILD_GRACE="$ota_grace_time"; BUILD_GRACE_REASON="OTA/build change"
fi
[ "$BUILD_CHANGED" -eq 1 ] && log "$BUILD_GRACE_REASON detected; extending boot readiness by ${BUILD_GRACE}s."
wait_limit=$((timeout + BUILD_GRACE))

boot_signal_summary() {
  local completed anim ce sysui summary
  completed=$(getprop sys.boot_completed 2>/dev/null); anim=$(getprop init.svc.bootanim 2>/dev/null); ce=$(getprop sys.user.0.ce_available 2>/dev/null)
  check_process "$systemui_process" && sysui=up || sysui=down
  summary="boot=${completed:-?}, bootanim=${anim:-n/a}, ce=${ce:-n/a}, $systemui_process=$sysui"
  printf '%s' "$summary"
}

boot_signals_ready() {
  local completed anim ce
  completed=$(getprop sys.boot_completed 2>/dev/null); [ "$completed" = 1 ] || return 1
  if [ "$boot_animation_required" = true ]; then
    anim=$(getprop init.svc.bootanim 2>/dev/null)
    [ -z "$anim" ] || [ "$anim" = stopped ] || return 1
  fi
  if [ "$ce_storage_required" = true ]; then
    ce=$(getprop sys.user.0.ce_available 2>/dev/null)
    [ -z "$ce" ] || [ "$ce" = 1 ] || return 1
  fi
  check_process "$systemui_process" || return 1
  return 0
}

setting_bool() {
  case "$(get_prop "$1" 2>/dev/null)" in
    false|0|no|off) return 1 ;;
    *) return 0 ;;
  esac
}

setting_int() {
  local key="$1" default="$2" min="$3" max="$4" value
  value="$(get_prop "$key" 2>/dev/null)"
  case "$value" in ''|*[!0-9]*) value="$default" ;; esac
  [ "$value" -lt "$min" ] 2>/dev/null && value="$min"
  [ "$value" -gt "$max" ] 2>/dev/null && value="$max"
  printf '%s' "$value"
}

resolve_home_package() {
  local user line pkg

  user="$(cmd activity get-current-user 2>/dev/null | tr -cd '0-9')"
  [ -n "$user" ] || user=0

  line="$(
    cmd package resolve-activity \
      --brief \
      --user "$user" \
      -a android.intent.action.MAIN \
      -c android.intent.category.HOME \
      2>/dev/null | tail -n 1
  )"

  case "$line" in
    */*) pkg="${line%%/*}" ;;
    *) pkg="${line%% *}" ;;
  esac

  case "$pkg" in
    package:*) pkg="${pkg#package:}" ;;
  esac

  case "$pkg" in
    ''|android|com.android.systemui|*' '*|*':'*) return 1 ;;
  esac

  printf '%s' "$pkg"
}

launcher_window_ready() {
  local pkg="$1" dump

  dump="$(dumpsys window 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp|mTopFullscreenOpaqueWindowState' | head -n 20)"

  [ -n "$dump" ] || return 0

  printf '%s\n' "$dump" | grep -F "$pkg" >/dev/null 2>&1
}

keyguard_showing() {
  dumpsys window policy 2>/dev/null | grep -Eq 'mShowingLockscreen=true|isStatusBarKeyguard=true|showing=true'
}

launcher_signal_summary() {
  local home="$1" status focus
  check_process "$home" && status=up || status=down
  if launcher_window_ready "$home"; then focus=ready; else focus=not-focused; fi
  printf 'home=%s, process=%s, window=%s' "$home" "$status" "$focus"
}

launcher_ready() {
  local home="$1"

  [ -n "$home" ] || return 0
  check_process "$home" || return 1

  if setting_bool launcher_focus_required; then
    if keyguard_showing; then
      log "Launcher focus check skipped while keyguard is showing."
      return 0
    fi
    launcher_window_ready "$home" || return 1
  fi

  return 0
}

wait_for_launcher_ready() {
  setting_bool launcher_check_required || {
    log "Launcher readiness check disabled."
    return 0
  }

  local home wait start now elapsed last_log

  home="$(resolve_home_package 2>/dev/null || true)"
  if [ -z "$home" ]; then
    log "Launcher readiness check skipped: unable to resolve default HOME package."
    return 0
  fi

  wait="$(setting_int launcher_wait 45 0 180)"
  [ "$wait" -le 0 ] && {
    log "Launcher readiness check skipped: launcher_wait=0."
    return 0
  }

  log "Waiting for launcher readiness: $home (${wait}s)"
  start="$(date +%s)"
  last_log=-5

  while :; do
    now="$(date +%s)"
    elapsed=$((now - start))

    if launcher_ready "$home"; then
      log "Launcher ready after ${elapsed}s: $(launcher_signal_summary "$home")"
      return 0
    fi

    [ "$elapsed" -ge "$wait" ] && {
      trigger_crash_reboot "launcher did not become ready within ${wait}s ($(launcher_signal_summary "$home"))"
    }

    if [ $((elapsed - last_log)) -ge 5 ]; then
      log "Waiting for launcher (${elapsed}/${wait}s): $(launcher_signal_summary "$home")"
      last_log="$elapsed"
    fi

    sleep 1
  done
}

start_time=$(date +%s)
last_wait_log=-5
ready_count=0
while [ "$ready_count" -lt "$boot_ready_consecutive" ]; do
  now=$(date +%s); elapsed=$((now - start_time))
  [ "$elapsed" -ge "$wait_limit" ] && trigger_crash_reboot "boot signals did not become ready within ${wait_limit}s ($(boot_signal_summary))"
  if boot_signals_ready; then ready_count=$((ready_count + 1)); else ready_count=0; fi
  if [ $((elapsed - last_wait_log)) -ge 5 ]; then log "Waiting for stable boot signals (${elapsed}/${wait_limit}s): $(boot_signal_summary), consecutive=$ready_count/$boot_ready_consecutive"; last_wait_log="$elapsed"; fi
  [ "$ready_count" -lt "$boot_ready_consecutive" ] && sleep 1
done
boot_ready_time=$(date +%s); boot_elapsed=$((boot_ready_time - start_time))
log "Boot signals ready in ${boot_elapsed}s after $boot_ready_consecutive consecutive checks: $(boot_signal_summary)"
check_manual_fail_file_once
wait_for_launcher_ready

if [ -f "$COMPANION_PENDING" ] || [ "$(companion_installed_version_code)" -lt "$(companion_expected_version_code)" ]; then
  log "Installing or updating AshReXcue companion app after boot readiness."
  install_companion_app || log "Companion app installation remains pending."
fi
mark_boot_state monitoring "boot signals ready; stability monitoring active"

monitor_duration="$stability_time"
if [ "$BUILD_CHANGED" -eq 1 ]; then
  build_monitor_extra=$((BUILD_GRACE / 3))
  [ "$build_monitor_extra" -gt 60 ] && build_monitor_extra=60
  monitor_duration=$((monitor_duration + build_monitor_extra))
fi
log "Starting configurable stability monitoring for ${monitor_duration}s"
log "SystemUI=$systemui_process | extra=$extra_stability | processes=${monitored_processes:-none} | failures=$failure_threshold | interval=${check_interval}s | restart_limit=$restart_limit | process_action=$missing_process_action"

consecutive_failures=0
systemui_last_pid=""
systemui_restart_count=0
stability_start=$(date +%s); stability_end=$((stability_start + monitor_duration)); last_log_time=0
while :; do
  current_time=$(date +%s); [ "$current_time" -ge "$stability_end" ] && break
  required_failure=0
  current_sysui_pid=$(first_pid_of "$systemui_process")
  if [ -z "$current_sysui_pid" ]; then
    required_failure=1; log "Warning required process missing: $systemui_process"
  else
    if [ -n "$systemui_last_pid" ] && [ "$current_sysui_pid" != "$systemui_last_pid" ]; then
      systemui_restart_count=$((systemui_restart_count + 1)); log "Warning $systemui_process restarted ($systemui_restart_count/$restart_limit)"
      [ "$systemui_restart_count" -ge "$restart_limit" ] && trigger_crash_reboot "$systemui_process exceeded restart limit"
    fi
    systemui_last_pid="$current_sysui_pid"
  fi

  if [ "$extra_stability" = true ] && [ -n "$monitored_processes" ]; then
    old_ifs="$IFS"; IFS=','
    for proc in $monitored_processes; do
      IFS="$old_ifs"; [ -n "$proc" ] || continue
      if ! check_process "$proc"; then
        log "Warning monitored process missing: $proc"
        [ "$missing_process_action" = rescue ] && required_failure=1
      fi
      IFS=','
    done
    IFS="$old_ifs"
  fi

  if [ "$required_failure" -eq 0 ]; then
    [ "$consecutive_failures" -gt 0 ] && log "Required process set recovered."
    consecutive_failures=0
  else
    consecutive_failures=$((consecutive_failures + 1)); log "Required process failure ${consecutive_failures}/${failure_threshold}"
    [ "$consecutive_failures" -ge "$failure_threshold" ] && trigger_crash_reboot "required processes failed $failure_threshold consecutive checks"
  fi
  if [ $((current_time - last_log_time)) -ge 10 ]; then log "Stability progress: $((current_time - stability_start))/${monitor_duration}s"; last_log_time="$current_time"; fi
  sleep "$check_interval"
done

log "Stability checks passed. Device is stable."
complete_restore_trial || true
mark_rescue_boot_stable
if [ "$BUILD_CHANGED" -eq 1 ]; then
  ADAPTIVE_TIMEOUT=$(get_prop timeout)
  log "Adaptive timeout learning skipped for $BUILD_GRACE_REASON boot (sample=${boot_elapsed}s)."
else
  update_adaptive_timeout "$boot_elapsed"
  log "Adaptive timeout: sample=${boot_elapsed}s median=${ADAPTIVE_MEDIAN}s new=${ADAPTIVE_TIMEOUT}s range=${timeout_min}-${timeout_max}s"
fi

if [ -f "$TMP_FILE" ]; then
  if [ -f "$MODULE_LIST" ] && [ -x "$JQ" ]; then
    changed_modules=$("$JQ" -n -r --slurpfile new "$TMP_FILE" --slurpfile old "$MODULE_LIST" '
      ($new[0] // [])[] as $n |
      (($old[0] // []) | map(select(((.folder // .id) == $n.folder) or (.id == $n.id))) | .[0] // null) as $o |
      if $o == null then "Added: \($n.name) (\($n.id)) [\($n.folder)]"
      elif (($o.fingerprint // "") != "" and ($n.fingerprint != $o.fingerprint or $n.id != $o.id)) then "Updated: \($n.name) (\($n.id)) [\($n.folder)] \($o.version)->\($n.version)"
      elif (($o.fingerprint // "") == "" and ($n.name != $o.name or $n.version != $o.version or $n.versionCode != $o.versionCode or $n.id != $o.id)) then "Updated: \($n.name) (\($n.id)) [\($n.folder)] \($o.version)->\($n.version)"
      elif $n.status != ($o.status // $n.status) then "Status: \($n.name) (\($n.id)) [\($n.folder)] \($o.status)->\($n.status)"
      else empty end,
      (($old[0] // [])[] as $o | select((($new[0] // []) | map(select((.folder == ($o.folder // $o.id)) or (.id == $o.id))) | length) == 0) | "Removed: \($o.name) (\($o.id)) [\($o.folder // $o.id)]")
    ' 2>/dev/null)
    if [ -n "$changed_modules" ]; then log "Module changes detected:"; printf '%s\n' "$changed_modules" | while IFS= read -r line; do log "$line"; done; else log "No module changes detected"; fi
  fi
  sync_path "$TMP_FILE" 2>/dev/null || true
  mv -f "$TMP_FILE" "$MODULE_LIST" && log "Stable module snapshot committed."
fi

modify_prop loops 0
modify_prop rescue_stage 0
modify_prop disable none
save_stable_build
mark_boot_state stable "stability window completed"
log "Loop counter and protection stage reset only after full stability success."

# Remove stale trust entries while preserving AshReXcue as protected.
cleanup_module_list_setting protected_modules AshLooper
cleanup_module_list_setting trusted_modules ""
cleanup_module_list_setting suspect_modules ""
log "Trust categories cleaned and legacy whitelist synchronized."

log "Native companion state finalized; no WebUI runtime is present."
log "######## THE END ##########"

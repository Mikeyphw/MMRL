#!/system/bin/sh

SETTINGS="${ASH_TEST_SETTINGS:-$MODPATH/settings.prop}"
mdir="${ASH_TEST_MODULES_DIR:-/data/adb/modules}"
ASHLOOPER_DIR="${ASH_TEST_STATE_DIR:-/data/adb/ashlooper}"
LOG_DIR="$ASHLOOPER_DIR/logs"
LEGACY_LOG_DIR="/cache/looper"
TMP_FILE="$ASHLOOPER_DIR/tmp_modules.json"
MODULE_LIST="$ASHLOOPER_DIR/module.json"
BOOT_STATE="$ASHLOOPER_DIR/boot_state.prop"
BOOT_HISTORY="$ASHLOOPER_DIR/boot_times"
BUILD_STATE="$ASHLOOPER_DIR/stable_build"
RESCUE_DIR="$ASHLOOPER_DIR/rescue"
RESCUE_HISTORY_DIR="$RESCUE_DIR/history"
QUARANTINE_DIR="$RESCUE_DIR/quarantine"
LATEST_RESCUE="$RESCUE_DIR/latest.json"
RESTORE_STATE="$RESCUE_DIR/restore_state.prop"
RESTORE_TRIAL="$RESCUE_DIR/restore_trial.tsv"
RESTORE_HISTORY_DIR="$RESCUE_DIR/restore_history"
JQ="${ASH_TEST_JQ:-$MODPATH/jq/jq}"
boot_completed=0

chooseport() {
  [ "$1" ] && local delay=$1 || local delay=10
  local retry_count=0 max_retries=2 count
  [ -n "$TMPDIR" ] || TMPDIR="/data/local/tmp"
  mkdir -p "$TMPDIR"
  while true; do
    count=0
    while true; do
      timeout "$delay" /system/bin/getevent -lqc 1 2>&1 > "$TMPDIR/events" &
      sleep 0.5
      count=$((count + 1))
      grep -q 'KEY_VOLUMEUP *DOWN' "$TMPDIR/events" 2>/dev/null && return 0
      grep -q 'KEY_VOLUMEDOWN *DOWN' "$TMPDIR/events" 2>/dev/null && return 1
      [ "$count" -gt 12 ] && break
    done
    retry_count=$((retry_count + 1))
    if [ "$retry_count" -gt "$max_retries" ]; then
      echo "  > Volume key not detected after $max_retries attempts. Auto-selecting Current Option."
      return 0
    fi
    echo "  > Volume key not detected. Attempt $retry_count of $max_retries. Try again"
  done
}

delete() { rm -f "$@"; }
delete_recursive() { rm -rf "$@"; }

find_busybox() {
  for candidate in /data/adb/ksu/bin/busybox /data/adb/magisk/busybox /data/adb/ap/bin/busybox /system/bin/busybox; do
    if [ -f "$candidate" ] && [ -x "$candidate" ] && "$candidate" true >/dev/null 2>&1; then
      echo "$candidate"
      return 0
    fi
  done
  if command -v busybox >/dev/null 2>&1; then
    candidate=$(command -v busybox)
    if "$candidate" true >/dev/null 2>&1; then
      echo "$candidate"
      return 0
    fi
  fi
  return 1
}

if [ -d /data/adb/ksu ] || [ -f /data/adb/ksu/ksu ]; then
  KSU=1
elif [ -f /data/adb/apd ] && [ -d /data/adb/ap ]; then
  APATCH=1
elif [ -d /data/adb/magisk ] || [ -f /data/adb/magisk/magisk ]; then
  MAGISK=1
fi

if [ "$KSU" ]; then
  method="KernelSU"
elif [ "$APATCH" ]; then
  method="APatch"
elif [ "$MAGISK" ]; then
  method="Magisk"
elif [ -w "/data/adb/modules" ]; then
  method="Unknown Root"
else
  method="Unknown"
fi

get_root_version() {
  local version="" ksu_ver magisk_name magisk_code magisk_ver
  if [ "$APATCH" ]; then
    version=$(/data/adb/apd -V 2>/dev/null | head -n 1)
  fi
  if [ "$KSU" ]; then
    ksu_ver=$(/data/adb/ksud -V 2>/dev/null | head -n 1)
    [ -n "$version" ] && version="$version | $ksu_ver" || version="$ksu_ver"
  fi
  if [ "$MAGISK" ]; then
    magisk_name=$(/data/adb/magisk/magisk -v 2>/dev/null | head -n 1)
    magisk_code=$(/data/adb/magisk/magisk -V 2>/dev/null | head -n 1)
    magisk_ver="${magisk_name} (${magisk_code})"
    [ -n "$version" ] && version="$version | $magisk_ver" || version="$magisk_ver"
  fi
  echo "${version:-Unknown}"
}

get_prop() {
  local prop="$1" target_file="${2:-$SETTINGS}" value
  [ -f "$target_file" ] || return 1
  value=$(grep "^$prop=" "$target_file" 2>/dev/null | head -n 1 | cut -d'=' -f2-)
  value=$(printf '%s' "$value" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' -e 's/\r$//')
  printf '%s' "$value"
}

atomic_set_prop() {
  local key="$1" value="$2" target_file="$3" tmp
  [ -f "$target_file" ] || return 1
  case "$value" in *'
'*) return 1 ;; esac
  tmp="$target_file.tmp.$$"
  awk -v k="$key" -v v="$value" '
    BEGIN { found=0 }
    index($0, k "=") == 1 { print k "=" v; found=1; next }
    { print }
    END { if (!found) print k "=" v }
  ' "$target_file" > "$tmp" || { rm -f "$tmp"; return 1; }
  chmod --reference="$target_file" "$tmp" 2>/dev/null || chmod 600 "$tmp" 2>/dev/null
  mv -f "$tmp" "$target_file"
}

modify_prop() {
  local silent=false action value target_file current new_value
  if [ "$1" = "-s" ]; then silent=true; shift; fi
  action="$1"; value="$2"; target_file="${3:-$SETTINGS}"
  [ -f "$target_file" ] || { [ "$silent" = true ] || log "Error: File $target_file not found."; return 1; }
  if [ "$action" = "+" ]; then
    current=$(get_prop "$value" "$target_file")
    case "$current" in ''|*[!0-9]*) current=0 ;; esac
    new_value=$((current + 1))
    atomic_set_prop "$value" "$new_value" "$target_file" || return 1
    [ "$silent" = true ] || log "Increased $value in $(basename "$target_file"): $current → $new_value"
  else
    atomic_set_prop "$action" "$value" "$target_file" || return 1
    [ "$silent" = true ] || log "Set $action to $value in $(basename "$target_file")"
  fi
}

set_log_file() {
  local val f
  val=$(get_prop log); [ -n "$val" ] || val="1-0"
  f=$(echo "$val" | cut -d'-' -f1)
  LOG_FILE="$LOG_DIR/AshReXcueSession-$f.log"
}

rotate_logs() {
  local val f b
  mkdir -p "$LOG_DIR"
  val=$(get_prop log); [ -n "$val" ] || val="1-0"
  f=$(echo "$val" | cut -d'-' -f1); b=$(echo "$val" | cut -d'-' -f2)
  case "$f" in ''|*[!0-9]*) f=1 ;; esac
  case "$b" in ''|*[!0-9]*) b=0 ;; esac
  b=$((b + 1))
  if [ "$b" -gt 10 ]; then
    b=1; f=$((f + 1)); [ "$f" -gt 10 ] && f=1
  fi
  LOG_FILE="$LOG_DIR/AshReXcueSession-$f.log"
  [ "$b" -eq 1 ] && { rm -f "$LOG_FILE"; : > "$LOG_FILE"; }
  CURRENT_BOOT="$b"
  modify_prop -s "log" "$f-$b"
}

log() {
  [ -n "$LOG_FILE" ] || set_log_file
  mkdir -p "$LOG_DIR" 2>/dev/null
  local timestamp
  timestamp=$(date '+%T' 2>/dev/null || echo "00:00:00")
  printf '[%s] >[%s]<\n\n' "$timestamp" "$1" >> "$LOG_FILE" 2>/dev/null
}

sanitize_int_setting() {
  local key="$1" default="$2" min="$3" max="$4" value
  value=$(get_prop "$key")
  case "$value" in ''|*[!0-9-]*) value="$default" ;; esac
  [ "$value" -lt "$min" ] 2>/dev/null && value="$min"
  [ "$value" -gt "$max" ] 2>/dev/null && value="$max"
  [ "$(get_prop "$key")" = "$value" ] || modify_prop -s "$key" "$value"
  eval "$key=\"$value\""
}

sanitize_bool_setting() {
  local key="$1" default="$2" value
  value=$(get_prop "$key")
  case "$value" in true|false) ;; *) value="$default" ;; esac
  [ "$(get_prop "$key")" = "$value" ] || modify_prop -s "$key" "$value"
  eval "$key=\"$value\""
}

validate_settings_v99() {
  mkdir -p "$ASHLOOPER_DIR"
  sanitize_int_setting timeout_min 60 45 300
  sanitize_int_setting timeout_max 300 "$timeout_min" 900
  sanitize_int_setting timeout 60 "$timeout_min" "$timeout_max"
  sanitize_int_setting timeout_margin 30 10 180
  sanitize_int_setting timeout_history_samples 5 3 10
  sanitize_int_setting timeout_decrease_step 10 1 30
  sanitize_int_setting stability_time 60 30 600
  sanitize_int_setting threshold 2 1 5
  sanitize_int_setting loops 0 0 99
  sanitize_int_setting failure_threshold 3 1 10
  sanitize_int_setting check_interval 3 1 30
  sanitize_int_setting restart_limit 3 1 10
  sanitize_int_setting boot_ready_consecutive 3 1 10
  sanitize_int_setting first_boot_grace 90 0 600
  sanitize_int_setting ota_grace_time 180 0 900
  sanitize_bool_setting extra_stability false
  sanitize_bool_setting boot_animation_required true
  sanitize_bool_setting ce_storage_required true

  mode=$(get_prop mode); case "$mode" in 1|2) ;; *) mode=1; modify_prop -s mode "$mode" ;; esac
  disable=$(get_prop disable); case "$disable" in none|partial|full) ;; *) disable=none; modify_prop -s disable "$disable" ;; esac
  missing_process_action=$(get_prop missing_process_action)
  case "$missing_process_action" in warn|rescue) ;; *) missing_process_action=rescue; modify_prop -s missing_process_action "$missing_process_action" ;; esac

  systemui_process=$(get_prop systemui_process)
  case "$systemui_process" in ''|*[!A-Za-z0-9._:-]*) systemui_process="com.android.systemui"; modify_prop -s systemui_process "$systemui_process" ;; esac
  monitored_processes=$(get_prop monitored_processes)
  case "$monitored_processes" in *[!A-Za-z0-9._:,-]*) monitored_processes="servicemanager,vold"; modify_prop -s monitored_processes "$monitored_processes" ;; esac
}

start_run() {
  mkdir -p "$ASHLOOPER_DIR"
  rotate_logs
  local current_date current_full install_date rtc_status mode_val disable_val extra
  current_date=$(date '+%Y-%m-%d' 2>/dev/null || echo "1970-01-01")
  current_full=$(date '+%d.%m.%y %T' 2>/dev/null || echo "Unknown")
  install_date=$(get_prop install_date); rtc_status="CORRECT"
  mode_val=$(get_prop mode); disable_val=$(get_prop disable); extra=$(get_prop extra_stability)
  if [ "$install_date" != "none" ] && [ "$install_date" != "unknown" ] && [ "$current_date" \< "$install_date" ]; then
    rtc_status="BACKWARD ($current_date < $install_date)"
  fi
  log "◆◆◆◆◆◆◆ NEW BOOT $CURRENT_BOOT ◆◆◆◆◆◆◆◆"
  log "AshReXcue Process Started"
  log "Date: $current_full | RTC Status: $rtc_status"
  log "Executing post-fs-data.sh"
  log "Running on $ROOT_TYPE"
  log "Root Version: $(get_root_version)"
  log "Boot reason: $(getprop sys.boot.reason 2>/dev/null || echo Unknown)"
  log "Device: $(getprop ro.product.model 2>/dev/null || echo Unknown)"
  log "Android: $(getprop ro.build.version.release 2>/dev/null || echo Unknown)"
  log "Module Version: $(get_prop version "$MODPATH/module.prop" 2>/dev/null || echo Unknown)"
  log "Module Version Code: $(get_prop versionCode "$MODPATH/module.prop" 2>/dev/null || echo Unknown)"
  log "Mode: $mode_val | Disable: $disable_val | Extra Stability: $extra"
}

current_boot_id() {
  if [ -r /proc/sys/kernel/random/boot_id ]; then cat /proc/sys/kernel/random/boot_id; else printf '%s-%s' "$(date +%s)" "$$"; fi
}

boot_state_value() { get_prop "$1" "$BOOT_STATE"; }

write_boot_state() {
  local state="$1" reason="$2" tmp boot_id started loops_now disable_now now
  mkdir -p "$ASHLOOPER_DIR"
  boot_id=$(current_boot_id); now=$(date +%s 2>/dev/null || echo 0)
  started=$(boot_state_value started_at 2>/dev/null || true); [ -n "$started" ] || started="$now"
  loops_now=$(get_prop loops); disable_now=$(get_prop disable)
  tmp="$BOOT_STATE.tmp.$$"
  {
    printf 'boot_id=%s\n' "$boot_id"
    printf 'state=%s\n' "$state"
    printf 'started_at=%s\n' "$started"
    printf 'updated_at=%s\n' "$now"
    printf 'loops=%s\n' "$loops_now"
    printf 'disable=%s\n' "$disable_now"
    printf 'reason=%s\n' "$(printf '%s' "$reason" | tr '\r\n=' '   ')"
  } > "$tmp" && chmod 600 "$tmp" 2>/dev/null && mv -f "$tmp" "$BOOT_STATE"
  modify_prop -s boot "$state" "$SETTINGS"
}

begin_boot_transaction() {
  local previous_state previous_id current_id
  previous_state=$(boot_state_value state); previous_id=$(boot_state_value boot_id); current_id=$(current_boot_id)
  case "$previous_state" in
    booting|monitoring)
      if [ -n "$previous_id" ] && [ "$previous_id" != "$current_id" ]; then
        log "Previous boot transaction was unfinished ($previous_state); retained loop strike for recovery."
      fi
      ;;
  esac
  write_boot_state booting "post-fs-data started"
}

mark_boot_state() { write_boot_state "$1" "$2"; }

hash_stream() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum | awk '{print $1}'; return; fi
  local bb; bb=$(find_busybox)
  if [ -n "$bb" ] && "$bb" sha256sum </dev/null >/dev/null 2>&1; then "$bb" sha256sum | awk '{print $1}'; return; fi
  cksum | awk '{print $1 "-" $2}'
}

hash_file() {
  [ -f "$1" ] || { echo "missing"; return; }
  if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" 2>/dev/null | awk '{print $1}'; return; fi
  local bb; bb=$(find_busybox)
  if [ -n "$bb" ] && "$bb" sha256sum "$1" >/dev/null 2>&1; then "$bb" sha256sum "$1" 2>/dev/null | awk '{print $1}'; return; fi
  cksum "$1" 2>/dev/null | awk '{print $1 "-" $2}'
}

stable_module_fingerprint() {
  local folder="$1" rel file
  {
    for rel in module.prop post-fs-data.sh service.sh action.sh customize.sh uninstall.sh system.prop sepolicy.rule; do
      file="$folder/$rel"
      [ -f "$file" ] && printf '%s:%s\n' "$rel" "$(hash_file "$file")"
    done
  } | hash_stream
}

json_escape() {
  printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' -e 's/\t/\\t/g' -e 's/\r/\\r/g'
}

create_mod_list() {
  local out first module_folder folder_name status id name version versionCode fingerprint
  mkdir -p "$ASHLOOPER_DIR"
  out="$TMP_FILE.tmp.$$"; first=1
  printf '[' > "$out"
  for module_folder in "$mdir"/*; do
    [ -d "$module_folder" ] || continue
    folder_name=$(basename "$module_folder")
    case "$folder_name" in ''|*[!A-Za-z0-9._-]*) log "Skipping unsafe module folder name: $folder_name"; continue ;; esac
    [ -f "$module_folder/disable" ] && status="disabled" || status="enabled"
    id="$folder_name"; name="$folder_name"; version="unknown"; versionCode="0"
    if [ -f "$module_folder/module.prop" ]; then
      prop_id=$(get_prop id "$module_folder/module.prop"); prop_name=$(get_prop name "$module_folder/module.prop")
      prop_version=$(get_prop version "$module_folder/module.prop"); prop_versionCode=$(get_prop versionCode "$module_folder/module.prop")
      case "$prop_id" in ''|*[!A-Za-z0-9._-]*) ;; *) id="$prop_id" ;; esac
      [ -n "$prop_name" ] && name="$prop_name"; [ -n "$prop_version" ] && version="$prop_version"; [ -n "$prop_versionCode" ] && versionCode="$prop_versionCode"
    fi
    fingerprint=$(stable_module_fingerprint "$module_folder")
    [ "$first" -eq 0 ] && printf ',' >> "$out"
    printf '\n  {"id":"%s","folder":"%s","name":"%s","version":"%s","versionCode":"%s","status":"%s","fingerprint":"%s"}' \
      "$(json_escape "$id")" "$(json_escape "$folder_name")" "$(json_escape "$name")" "$(json_escape "$version")" "$(json_escape "$versionCode")" "$status" "$fingerprint" >> "$out"
    first=0
  done
  printf '\n]\n' >> "$out"
  sync_path "$out" 2>/dev/null || true
  mv -f "$out" "$TMP_FILE"
}

is_whitelisted_module() {
  local id="$1" folder="$2" whitelist
  whitelist=$(get_prop whitelist | tr -d " '\"\r\n")
  case ",$whitelist," in *",$id,"*|*",$folder,"*) return 0 ;; esac
  return 1
}

list_modules() {
  local count=0 module_folder folder_name id status
  log "###############"; log "Available modules:"
  for module_folder in "$mdir"/*; do
    [ -d "$module_folder" ] || continue
    folder_name=$(basename "$module_folder"); id="$folder_name"
    [ -f "$module_folder/module.prop" ] && { prop_id=$(get_prop id "$module_folder/module.prop"); case "$prop_id" in ''|*[!A-Za-z0-9._-]*) ;; *) id="$prop_id" ;; esac; }
    [ -f "$module_folder/disable" ] && status="Disabled" || status="Active"
    is_whitelisted_module "$id" "$folder_name" && status="$status - Whitelist"
    count=$((count + 1)); log "$count. $folder_name ($id) - $status"
  done
  log "###############"
}

lockdown() {
  local MODE LOCKDOWN_TYPE module_folder folder_name id enabled_modules=0
  MODE=$(get_prop mode); LOCKDOWN_TYPE="${1:-normal}"
  [ "$LOCKDOWN_TYPE" = full ] && log "Full Lockdown: disabling ALL modules including AshLooper..." || log "Lockdown: disabling non-whitelisted modules..."
  for module_folder in "$mdir"/*; do
    [ -d "$module_folder" ] || continue
    folder_name=$(basename "$module_folder"); id="$folder_name"
    [ -f "$module_folder/module.prop" ] && { prop_id=$(get_prop id "$module_folder/module.prop"); case "$prop_id" in ''|*[!A-Za-z0-9._-]*) ;; *) id="$prop_id" ;; esac; }
    if [ "$LOCKDOWN_TYPE" != full ] && is_whitelisted_module "$id" "$folder_name"; then log "Skipping whitelisted module: $id"; continue; fi
    if [ ! -f "$module_folder/disable" ]; then touch "$module_folder/disable" && enabled_modules=$((enabled_modules + 1)); fi
    log "Disabled module: $id [$folder_name]"
  done
  log "Total newly disabled: $enabled_modules"
  modify_prop loops 0; modify_prop disable full
  [ "$MODE" = 2 ] && reboot recovery || reboot
}

disable_new_mods() {
  local MODE targets folder id disabled=0
  MODE=$(get_prop mode)
  [ -f "$MODULE_LIST" ] || { log "No previous module list found. Invoking lockdown."; lockdown; return; }
  [ -x "$JQ" ] || { log "jq unavailable. Invoking lockdown."; lockdown; return; }
  targets="$ASHLOOPER_DIR/changed_modules.$$"
  "$JQ" -n -r --slurpfile new "$TMP_FILE" --slurpfile old "$MODULE_LIST" '
    $new[0][] as $n |
    (($old[0] // []) | map(select(((.folder // .id) == $n.folder) or (.id == $n.id))) | .[0] // null) as $o |
    select(
      $o == null or
      (if (($o.fingerprint // "") != "") then
         ($n.fingerprint != $o.fingerprint or $n.id != $o.id)
       else
         ($n.name != $o.name or $n.version != $o.version or $n.versionCode != $o.versionCode or $n.id != $o.id)
       end)
    ) |
    [$n.folder, $n.id] | @tsv
  ' > "$targets" 2>/dev/null || : > "$targets"
  while IFS="$(printf '\t')" read -r folder id; do
    [ -n "$folder" ] || continue
    case "$folder" in ''|*/*|.|..|*[!A-Za-z0-9._-]*) log "Skipping unsafe module folder: $folder"; continue ;; esac
    case "$id" in ''|*[!A-Za-z0-9._-]*) id="$folder" ;; esac
    if is_whitelisted_module "$id" "$folder"; then log "Skipping changed whitelisted module: $id [$folder]"; continue; fi
    if [ -d "$mdir/$folder" ]; then
      touch "$mdir/$folder/disable" && { log "Module disabled: $id [$folder]"; disabled=$((disabled + 1)); }
    else
      log "Module folder not found: $mdir/$folder"
    fi
  done < "$targets"
  rm -f "$targets"
  [ "$disabled" -gt 0 ] || { log "No non-whitelisted new/updated modules detected. Falling back to lockdown."; lockdown; return; }
  modify_prop loops 0; modify_prop disable partial
  [ "$MODE" = 2 ] && reboot recovery || reboot
}

handle_boot_loop() {
  local loops_now disable_mode threshold_now
  loops_now=$(get_prop loops); disable_mode=$(get_prop disable); threshold_now=$(get_prop threshold)
  case "$loops_now" in ''|*[!0-9]*) loops_now=0 ;; esac
  case "$threshold_now" in ''|*[!0-9]*) threshold_now=2 ;; esac
  log "Boot loops: $loops_now/$threshold_now | Protection: $disable_mode"
  if [ "$loops_now" -ge "$threshold_now" ]; then
    case "$disable_mode" in
      none) log "Threshold reached - disabling new or updated modules"; disable_new_mods ;;
      partial) log "Threshold reached - activating lockdown"; lockdown ;;
      full) log "Full protection failed; disabling all modules including AshLooper."; lockdown full ;;
      *) log "Invalid protection mode; taking no action" ;;
    esac
  fi
  list_modules
}

trigger_crash_reboot() {
  local reason="${1:-stability failure}"
  log "Crash detected: $reason"
  mark_boot_state failed "$reason"
  handle_boot_loop
  log "Rebooting device to continue bootloop protection..."
  reboot
  sleep 5
  exit 1
}

current_build_identity() {
  printf '%s|%s' "$(getprop ro.build.fingerprint 2>/dev/null)" "$(getprop ro.build.version.incremental 2>/dev/null)"
}

build_changed_since_stable() {
  local current previous
  current=$(current_build_identity); previous=$(cat "$BUILD_STATE" 2>/dev/null)
  [ -z "$previous" ] || [ "$current" != "$previous" ]
}

save_stable_build() {
  local tmp="$BUILD_STATE.tmp.$$"
  current_build_identity > "$tmp" && chmod 600 "$tmp" 2>/dev/null && mv -f "$tmp" "$BUILD_STATE"
}

update_adaptive_timeout() {
  local elapsed="$1" history_tmp samples median desired current sorted count mid a b
  history_tmp="$BOOT_HISTORY.tmp.$$"
  { cat "$BOOT_HISTORY" 2>/dev/null; printf '%s\n' "$elapsed"; } | grep -E '^[0-9]+$' | tail -n "$timeout_history_samples" > "$history_tmp"
  mv -f "$history_tmp" "$BOOT_HISTORY"
  sorted="$BOOT_HISTORY.sorted.$$"; sort -n "$BOOT_HISTORY" > "$sorted"
  count=$(wc -l < "$sorted" | tr -d ' ')
  if [ "$count" -le 0 ]; then median="$elapsed"; elif [ $((count % 2)) -eq 1 ]; then mid=$(((count + 1) / 2)); median=$(sed -n "${mid}p" "$sorted"); else mid=$((count / 2)); a=$(sed -n "${mid}p" "$sorted"); b=$(sed -n "$((mid + 1))p" "$sorted"); median=$(((a + b) / 2)); fi
  rm -f "$sorted"
  desired=$((median + timeout_margin)); current=$(get_prop timeout)
  case "$current" in ''|*[!0-9]*) current="$timeout_min" ;; esac
  [ "$desired" -lt "$timeout_min" ] && desired="$timeout_min"
  [ "$desired" -gt "$timeout_max" ] && desired="$timeout_max"
  if [ "$desired" -lt "$current" ] && [ $((current - desired)) -gt "$timeout_decrease_step" ]; then desired=$((current - timeout_decrease_step)); fi
  [ "$desired" -lt "$timeout_min" ] && desired="$timeout_min"
  modify_prop timeout "$desired"
  ADAPTIVE_TIMEOUT="$desired"
  ADAPTIVE_MEDIAN="$median"
}

pid_cmdline_contains() {
  local pid="$1" marker="$2" cmdline
  case "$pid" in ''|*[!0-9]*) return 1 ;; esac
  [ -r "/proc/$pid/cmdline" ] || return 1
  cmdline=$(tr '\000' ' ' < "/proc/$pid/cmdline" 2>/dev/null)
  case "$cmdline" in *"$marker"*) return 0 ;; esac
  return 1
}

stop_owned_pid() {
  local pid_file="$1" marker="$2" pid
  [ -f "$pid_file" ] || return 0
  pid=$(cat "$pid_file" 2>/dev/null)
  if pid_cmdline_contains "$pid" "$marker"; then
    kill "$pid" 2>/dev/null
    sleep 1
    pid_cmdline_contains "$pid" "$marker" && kill -9 "$pid" 2>/dev/null
  fi
  rm -f "$pid_file"
}

# --- AshReXcue 10.0: rescue manifests, staged escalation/restoration, trust categories ---
initialize_storage() {
  mkdir -p "$ASHLOOPER_DIR" "$LOG_DIR" "$RESCUE_HISTORY_DIR" "$QUARANTINE_DIR" "$RESTORE_HISTORY_DIR" 2>/dev/null
  chmod 700 "$ASHLOOPER_DIR" "$LOG_DIR" "$RESCUE_DIR" "$RESCUE_HISTORY_DIR" "$QUARANTINE_DIR" "$RESTORE_HISTORY_DIR" 2>/dev/null
  if [ -d "$LEGACY_LOG_DIR" ] && [ ! -f "$LOG_DIR/.legacy_migrated" ]; then
    for legacy_log in "$LEGACY_LOG_DIR"/AshReXcueSession-*.log "$LEGACY_LOG_DIR"/looperbug.log; do
      [ -f "$legacy_log" ] || continue
      legacy_name=$(basename "$legacy_log")
      [ -f "$LOG_DIR/$legacy_name" ] || cp -p "$legacy_log" "$LOG_DIR/$legacy_name" 2>/dev/null
    done
    atomic_write_text "$LOG_DIR/.legacy_migrated" 600 "migrated
" || true
  fi
}

normalize_module_list_value() {
  local raw="$1" required="$2" token output="" old_ifs
  raw=$(printf '%s' "$raw" | tr -d " '\"\r\n")
  old_ifs="$IFS"; IFS=','
  for token in $raw; do
    IFS="$old_ifs"
    case "$token" in
      ''|*[!A-Za-z0-9._-]*) ;;
      *) case ",$output," in *",$token,"*) ;; *) [ -n "$output" ] && output="$output,$token" || output="$token" ;; esac ;;
    esac
    IFS=','
  done
  IFS="$old_ifs"
  if [ -n "$required" ]; then
    case ",$output," in *",$required,"*) ;; *) [ -n "$output" ] && output="$required,$output" || output="$required" ;; esac
  fi
  printf '%s' "$output"
}

sanitize_module_list_setting() {
  local key="$1" default="$2" required="$3" value normalized
  value=$(get_prop "$key"); [ -n "$value" ] || value="$default"
  normalized=$(normalize_module_list_value "$value" "$required")
  [ "$(get_prop "$key")" = "\"$normalized\"" ] || modify_prop -s "$key" "\"$normalized\""
  eval "$key=\"$normalized\""
}

validate_settings() {
  initialize_storage
  validate_settings_v99
  sanitize_int_setting rescue_stage 0 0 4

  rescue_stage=$(get_prop rescue_stage)
  case "$rescue_stage" in
    0) disable=none ;;
    1) disable=targeted ;;
    2) disable=normal ;;
    3) disable=trusted ;;
    4) disable=exhausted ;;
    *) rescue_stage=0; disable=none; modify_prop -s rescue_stage 0 ;;
  esac
  modify_prop -s disable "$disable"

  legacy_protected=$(get_prop whitelist)
  [ -n "$legacy_protected" ] || legacy_protected=$(get_prop protected_modules)
  protected_modules=$(normalize_module_list_value "$legacy_protected" AshLooper)
  modify_prop -s protected_modules "\"$protected_modules\""
  modify_prop -s whitelist "\"$protected_modules\""
  sanitize_module_list_setting trusted_modules "" ""
  sanitize_module_list_setting suspect_modules "" ""
}

safe_manifest_value() { printf '%s' "$1" | tr '\t\r\n=' '    '; }

list_setting_contains_module() {
  local key="$1" id="$2" folder="$3" values
  values=$(get_prop "$key" | tr -d " '\"\r\n")
  case ",$values," in *",$id,"*|*",$folder,"*) return 0 ;; esac
  return 1
}

base_module_trust() {
  local id="$1" folder="$2"
  if list_setting_contains_module protected_modules "$id" "$folder" || list_setting_contains_module whitelist "$id" "$folder"; then
    echo protected
  elif list_setting_contains_module trusted_modules "$id" "$folder"; then
    echo trusted
  elif list_setting_contains_module suspect_modules "$id" "$folder"; then
    echo suspect
  else
    echo normal
  fi
}

module_trust_category() {
  local id="$1" folder="$2"
  [ -f "$QUARANTINE_DIR/$folder.prop" ] && { echo quarantined; return; }
  base_module_trust "$id" "$folder"
}

is_whitelisted_module() { [ "$(base_module_trust "$1" "$2")" = protected ]; }
is_protected_module() { [ "$(base_module_trust "$1" "$2")" = protected ]; }

module_identity() {
  local module_folder="$1" folder id name
  folder=$(basename "$module_folder"); id="$folder"; name="$folder"
  if [ -f "$module_folder/module.prop" ]; then
    prop_id=$(get_prop id "$module_folder/module.prop")
    prop_name=$(get_prop name "$module_folder/module.prop")
    case "$prop_id" in ''|*[!A-Za-z0-9._-]*) ;; *) id="$prop_id" ;; esac
    [ -n "$prop_name" ] && name="$prop_name"
  fi
  MODULE_FOLDER="$folder"; MODULE_ID="$id"; MODULE_NAME="$name"
}

module_token_exists() {
  local token="$1" module_folder
  for module_folder in "$mdir"/*; do
    [ -d "$module_folder" ] || continue
    module_identity "$module_folder"
    [ "$token" = "$MODULE_FOLDER" ] || [ "$token" = "$MODULE_ID" ] || continue
    return 0
  done
  return 1
}

cleanup_module_list_setting() {
  local key="$1" required="$2" values token output="" old_ifs
  values=$(get_prop "$key" | tr -d " '\"\r\n")
  old_ifs="$IFS"; IFS=','
  for token in $values; do
    IFS="$old_ifs"
    case "$token" in
      ''|*[!A-Za-z0-9._-]*) ;;
      *)
        if [ "$token" = "$required" ] || module_token_exists "$token"; then
          case ",$output," in *",$token,"*) ;; *) [ -n "$output" ] && output="$output,$token" || output="$token" ;; esac
        else
          log "Trust cleanup: removing missing $key entry '$token'."
        fi
        ;;
    esac
    IFS=','
  done
  IFS="$old_ifs"
  if [ -n "$required" ]; then
    case ",$output," in *",$required,"*) ;; *) [ -n "$output" ] && output="$required,$output" || output="$required" ;; esac
  fi
  modify_prop -s "$key" "\"$output\""
  [ "$key" = protected_modules ] && modify_prop -s whitelist "\"$output\""
  return 0
}

list_remove_module_tokens() {
  local key="$1" id="$2" folder="$3" required="$4" values token output="" old_ifs
  values=$(get_prop "$key" | tr -d " '\"\r\n")
  old_ifs="$IFS"; IFS=','
  for token in $values; do
    IFS="$old_ifs"
    if [ -n "$token" ] && [ "$token" != "$id" ] && [ "$token" != "$folder" ]; then
      [ -n "$output" ] && output="$output,$token" || output="$token"
    fi
    IFS=','
  done
  IFS="$old_ifs"
  if [ -n "$required" ]; then
    case ",$output," in *",$required,"*) ;; *) [ -n "$output" ] && output="$required,$output" || output="$required" ;; esac
  fi
  modify_prop -s "$key" "\"$output\""
  [ "$key" = protected_modules ] && modify_prop -s whitelist "\"$output\""
  return 0
}

list_add_module_token() {
  local key="$1" token="$2" required="$3" values
  values=$(get_prop "$key" | tr -d " '\"\r\n")
  case ",$values," in *",$token,"*) ;; *) [ -n "$values" ] && values="$values,$token" || values="$token" ;; esac
  values=$(normalize_module_list_value "$values" "$required")
  modify_prop -s "$key" "\"$values\""
  [ "$key" = protected_modules ] && modify_prop -s whitelist "\"$values\""
  return 0
}

_set_module_trust_unlocked() {
  local category="$1" token="$2" module_folder found=0 id folder name
  case "$category" in protected|trusted|normal|suspect) ;; *) echo "Invalid trust category."; return 2 ;; esac
  case "$token" in ''|*[!A-Za-z0-9._-]*) echo "Invalid module identifier."; return 2 ;; esac
  for module_folder in "$mdir"/*; do
    [ -d "$module_folder" ] || continue
    module_identity "$module_folder"
    id="$MODULE_ID"; folder="$MODULE_FOLDER"; name="$MODULE_NAME"
    if [ "$token" = "$id" ] || [ "$token" = "$folder" ]; then found=1; break; fi
  done
  [ "$found" -eq 1 ] || { echo "Module not found."; return 1; }
  [ "$folder" = AshLooper ] && [ "$category" != protected ] && { echo "AshReXcue must remain protected."; return 2; }

  list_remove_module_tokens protected_modules "$id" "$folder" AshLooper
  list_remove_module_tokens trusted_modules "$id" "$folder" ""
  list_remove_module_tokens suspect_modules "$id" "$folder" ""
  case "$category" in
    protected) list_add_module_token protected_modules "$folder" AshLooper ;;
    trusted) list_add_module_token trusted_modules "$folder" "" ;;
    suspect) list_add_module_token suspect_modules "$folder" "" ;;
  esac
  log "Trust category changed: $id [$folder] -> $category"
  echo "$name is now $category."
}

create_mod_list() {
  local out first module_folder folder_name status id name version versionCode fingerprint base_trust trust quarantined
  mkdir -p "$ASHLOOPER_DIR"
  out="$TMP_FILE.tmp.$$"; first=1
  printf '[' > "$out"
  for module_folder in "$mdir"/*; do
    [ -d "$module_folder" ] || continue
    folder_name=$(basename "$module_folder")
    case "$folder_name" in ''|*[!A-Za-z0-9._-]*) log "Skipping unsafe module folder name: $folder_name"; continue ;; esac
    [ -f "$module_folder/disable" ] && status=disabled || status=enabled
    id="$folder_name"; name="$folder_name"; version=unknown; versionCode=0
    if [ -f "$module_folder/module.prop" ]; then
      prop_id=$(get_prop id "$module_folder/module.prop"); prop_name=$(get_prop name "$module_folder/module.prop")
      prop_version=$(get_prop version "$module_folder/module.prop"); prop_versionCode=$(get_prop versionCode "$module_folder/module.prop")
      case "$prop_id" in ''|*[!A-Za-z0-9._-]*) ;; *) id="$prop_id" ;; esac
      [ -n "$prop_name" ] && name="$prop_name"; [ -n "$prop_version" ] && version="$prop_version"; [ -n "$prop_versionCode" ] && versionCode="$prop_versionCode"
    fi
    base_trust=$(base_module_trust "$id" "$folder_name")
    if [ -f "$QUARANTINE_DIR/$folder_name.prop" ]; then trust=quarantined; quarantined=true; else trust="$base_trust"; quarantined=false; fi
    fingerprint=$(stable_module_fingerprint "$module_folder")
    [ "$first" -eq 0 ] && printf ',' >> "$out"
    printf '\n  {"id":"%s","folder":"%s","name":"%s","version":"%s","versionCode":"%s","status":"%s","fingerprint":"%s","trust":"%s","baseTrust":"%s","quarantined":%s}' \
      "$(json_escape "$id")" "$(json_escape "$folder_name")" "$(json_escape "$name")" "$(json_escape "$version")" "$(json_escape "$versionCode")" "$status" "$fingerprint" "$trust" "$base_trust" "$quarantined" >> "$out"
    first=0
  done
  printf '\n]\n' >> "$out"
  sync_path "$out" 2>/dev/null || true
  mv -f "$out" "$TMP_FILE"
}

list_modules_with_trust_tsv() {
  local module_folder status trust quarantined
  for module_folder in "$mdir"/*; do
    [ -d "$module_folder" ] || continue
    module_identity "$module_folder"
    [ -f "$module_folder/disable" ] && status=disabled || status=enabled
    trust=$(base_module_trust "$MODULE_ID" "$MODULE_FOLDER")
    [ -f "$QUARANTINE_DIR/$MODULE_FOLDER.prop" ] && quarantined=true || quarantined=false
    printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$MODULE_FOLDER" "$MODULE_ID" "$(safe_manifest_value "$MODULE_NAME")" "$trust" "$quarantined" "$status"
  done | sort
}

list_modules() {
  local count=0 module_folder status trust
  log "###############"; log "Available modules:"
  for module_folder in "$mdir"/*; do
    [ -d "$module_folder" ] || continue
    module_identity "$module_folder"
    [ -f "$module_folder/disable" ] && status=Disabled || status=Active
    trust=$(module_trust_category "$MODULE_ID" "$MODULE_FOLDER")
    count=$((count + 1)); log "$count. $MODULE_FOLDER ($MODULE_ID) - $status - $trust"
  done
  log "###############"
}

new_rescue_id() {
  local stamp boot_short
  stamp=$(date '+%Y%m%dT%H%M%S' 2>/dev/null || date +%s)
  boot_short=$(current_boot_id | cut -c1-8)
  printf '%s-%s' "$stamp" "$boot_short"
}

begin_rescue_transaction() {
  state_lock_acquire || { log "Failed to acquire rescue transaction lock."; return 1; }
  RESCUE_LOCK_HELD=1
  RESCUE_STAGE_NAME="$1"; RESCUE_REASON=$(safe_manifest_value "$2"); RESCUE_ID=$(new_rescue_id)
  RESCUE_TSV="$RESCUE_DIR/$RESCUE_ID.tsv"; RESCUE_DISABLED_COUNT=0
  atomic_write_text "$RESCUE_TSV" 600 "" || { RESCUE_LOCK_HELD=0; state_lock_release; return 1; }
  log "Rescue transaction $RESCUE_ID started: stage=$RESCUE_STAGE_NAME reason=$RESCUE_REASON"
}

write_quarantine_marker() {
  local folder="$1" id="$2" name="$3" trust="$4" reason="$5" marker tmp now
  marker="$QUARANTINE_DIR/$folder.prop"; [ -f "$marker" ] && return 0
  now=$(date +%s 2>/dev/null || echo 0); tmp="$marker.tmp.$$"
  {
    printf 'rescue_id=%s\n' "$RESCUE_ID"
    printf 'folder=%s\n' "$folder"
    printf 'id=%s\n' "$id"
    printf 'name=%s\n' "$(safe_manifest_value "$name")"
    printf 'base_trust=%s\n' "$trust"
    printf 'reason=%s\n' "$(safe_manifest_value "$reason")"
    printf 'disabled_at=%s\n' "$now"
    printf 'was_enabled=true\n'
    printf 'disable_created=true\n'
  } > "$tmp" || return 1
  cat "$tmp" | atomic_write_file "$marker" 600
  rm -f "$tmp"
}

disable_module_for_rescue() {
  local folder="$1" id="$2" name="$3" trust="$4" reason="$5" module_dir
  case "$folder" in ''|*/*|.|..|*[!A-Za-z0-9._-]*) log "Skipping unsafe rescue folder: $folder"; return 1 ;; esac
  module_dir="$mdir/$folder"; [ -d "$module_dir" ] || { log "Rescue target missing: $id [$folder]"; return 1; }
  if [ -f "$module_dir/disable" ]; then
    if [ -f "$QUARANTINE_DIR/$folder.prop" ]; then
      log "Already quarantined: $id [$folder]"
    else
      log "Already disabled by user/root manager; not claiming ownership: $id [$folder]"
    fi
    return 2
  fi
  touch "$module_dir/disable" || { log "Failed to disable rescue target: $id [$folder]"; return 1; }
  write_quarantine_marker "$folder" "$id" "$name" "$trust" "$reason"
  printf '%s\t%s\t%s\t%s\t%s\n' "$folder" "$id" "$(safe_manifest_value "$name")" "$trust" "$(safe_manifest_value "$reason")" >> "$RESCUE_TSV"
  RESCUE_DISABLED_COUNT=$((RESCUE_DISABLED_COUNT + 1))
  log "Quarantined module: $id [$folder] trust=$trust reason=$reason"
  return 0
}

finalize_rescue_transaction() {
  local stage="$1" reason="$2" status="${3:-quarantined}" manifest tmp first folder id name trust module_reason created_at
  manifest="$RESCUE_HISTORY_DIR/$RESCUE_ID.json"; tmp="$manifest.tmp.$$"; created_at=$(date +%s 2>/dev/null || echo 0); first=1
  {
    printf '{\n'
    printf '  "schema": 1,\n'
    printf '  "rescueId": "%s",\n' "$(json_escape "$RESCUE_ID")"
    printf '  "bootId": "%s",\n' "$(json_escape "$(current_boot_id)")"
    printf '  "createdAt": %s,\n' "$created_at"
    printf '  "stage": "%s",\n' "$(json_escape "$stage")"
    printf '  "reason": "%s",\n' "$(json_escape "$reason")"
    printf '  "status": "%s",\n' "$(json_escape "$status")"
    printf '  "disabledCount": %s,\n' "$RESCUE_DISABLED_COUNT"
    printf '  "modules": ['
    while IFS="$(printf '\t')" read -r folder id name trust module_reason; do
      [ -n "$folder" ] || continue
      [ "$first" -eq 0 ] && printf ','
      printf '\n    {"folder":"%s","id":"%s","name":"%s","trust":"%s","wasEnabled":true,"disableCreated":true,"reason":"%s"}' \
        "$(json_escape "$folder")" "$(json_escape "$id")" "$(json_escape "$name")" "$(json_escape "$trust")" "$(json_escape "$module_reason")"
      first=0
    done < "$RESCUE_TSV"
    [ "$first" -eq 0 ] && printf '\n  '
    printf ']\n}\n'
  } > "$tmp" || { [ "${RESCUE_LOCK_HELD:-0}" = 1 ] && { RESCUE_LOCK_HELD=0; state_lock_release; }; return 1; }
  cat "$tmp" | atomic_write_file "$manifest" 600 || { rm -f "$tmp"; [ "${RESCUE_LOCK_HELD:-0}" = 1 ] && { RESCUE_LOCK_HELD=0; state_lock_release; }; return 1; }
  rm -f "$tmp"
  atomic_copy_file "$manifest" "$LATEST_RESCUE" 600 || true
  rm -f "$RESCUE_TSV"
  log "Rescue manifest saved: $manifest ($RESCUE_DISABLED_COUNT module(s))"
  if [ "${RESCUE_LOCK_HELD:-0}" = 1 ]; then RESCUE_LOCK_HELD=0; state_lock_release; fi
}

changed_module_targets() {
  [ -f "$MODULE_LIST" ] && [ -f "$TMP_FILE" ] && [ -x "$JQ" ] || return 0
  "$JQ" -n -r --slurpfile new "$TMP_FILE" --slurpfile old "$MODULE_LIST" '
    $new[0][] as $n |
    (($old[0] // []) | map(select(((.folder // .id) == $n.folder) or (.id == $n.id))) | .[0] // null) as $o |
    select($o == null or (if (($o.fingerprint // "") != "") then ($n.fingerprint != $o.fingerprint or $n.id != $o.id) else ($n.name != $o.name or $n.version != $o.version or $n.versionCode != $o.versionCode or $n.id != $o.id) end)) |
    [$n.folder, $n.id, $n.name] | @tsv
  ' 2>/dev/null
}

perform_rescue_reboot() {
  local mode
  mode=$(get_prop mode)
  [ "$mode" = 2 ] && reboot recovery || reboot
  sleep 5
  exit 0
}

rescue_targeted_modules() {
  local module_folder trust targets folder id name
  begin_rescue_transaction targeted "$1"
  for module_folder in "$mdir"/*; do
    [ -d "$module_folder" ] || continue
    module_identity "$module_folder"; trust=$(base_module_trust "$MODULE_ID" "$MODULE_FOLDER")
    [ "$trust" = suspect ] || continue
    disable_module_for_rescue "$MODULE_FOLDER" "$MODULE_ID" "$MODULE_NAME" "$trust" "classified suspect" || :
  done
  targets="$RESCUE_DIR/changed_targets.$$"; changed_module_targets > "$targets"
  while IFS="$(printf '\t')" read -r folder id name; do
    [ -n "$folder" ] || continue; trust=$(base_module_trust "$id" "$folder")
    case "$trust" in
      protected) log "Skipping changed protected module: $id [$folder]" ;;
      trusted) log "Deferring changed trusted module until trusted escalation: $id [$folder]" ;;
      *) disable_module_for_rescue "$folder" "$id" "$name" "$trust" "new or updated since stable boot" || : ;;
    esac
  done < "$targets"
  rm -f "$targets"
  finalize_rescue_transaction targeted "$RESCUE_REASON"
  modify_prop rescue_stage 1; modify_prop disable targeted; modify_prop loops 0
  log "Targeted rescue completed with $RESCUE_DISABLED_COUNT newly quarantined module(s)."
  perform_rescue_reboot
}

rescue_normal_modules() {
  local module_folder trust
  begin_rescue_transaction normal "$1"
  for module_folder in "$mdir"/*; do
    [ -d "$module_folder" ] || continue
    module_identity "$module_folder"; trust=$(base_module_trust "$MODULE_ID" "$MODULE_FOLDER")
    case "$trust" in protected|trusted) continue ;; *) disable_module_for_rescue "$MODULE_FOLDER" "$MODULE_ID" "$MODULE_NAME" "$trust" "normal escalation" || : ;; esac
  done
  finalize_rescue_transaction normal "$RESCUE_REASON"
  modify_prop rescue_stage 2; modify_prop disable normal; modify_prop loops 0
  log "Normal rescue escalation completed with $RESCUE_DISABLED_COUNT newly quarantined module(s)."
  perform_rescue_reboot
}

rescue_trusted_modules() {
  local module_folder trust
  begin_rescue_transaction trusted "$1"
  for module_folder in "$mdir"/*; do
    [ -d "$module_folder" ] || continue
    module_identity "$module_folder"; trust=$(base_module_trust "$MODULE_ID" "$MODULE_FOLDER")
    [ "$trust" = protected ] && { log "Protected module retained: $MODULE_ID [$MODULE_FOLDER]"; continue; }
    disable_module_for_rescue "$MODULE_FOLDER" "$MODULE_ID" "$MODULE_NAME" "$trust" "trusted escalation" || :
  done
  finalize_rescue_transaction trusted "$RESCUE_REASON"
  modify_prop rescue_stage 3; modify_prop disable trusted; modify_prop loops 0
  log "Trusted rescue escalation completed with $RESCUE_DISABLED_COUNT newly quarantined module(s)."
  perform_rescue_reboot
}

rescue_exhausted() {
  begin_rescue_transaction exhausted "$1"
  finalize_rescue_transaction exhausted "$RESCUE_REASON" exhausted
  modify_prop rescue_stage 4; modify_prop disable exhausted; modify_prop loops 0
  log "Automatic rescue exhausted. Protected modules remain enabled; recovery/manual diagnosis is required."
  reboot recovery 2>/dev/null || reboot
  sleep 5
  exit 0
}

list_quarantined_modules_tsv() {
  local marker folder id name trust rescue_id disabled_at
  for marker in "$QUARANTINE_DIR"/*.prop; do
    [ -f "$marker" ] || continue
    folder=$(get_prop folder "$marker"); id=$(get_prop id "$marker"); name=$(get_prop name "$marker")
    trust=$(get_prop base_trust "$marker"); rescue_id=$(get_prop rescue_id "$marker"); disabled_at=$(get_prop disabled_at "$marker")
    printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$folder" "$id" "$name" "$trust" "$rescue_id" "$disabled_at"
  done | sort
}

quarantine_count() {
  local marker count=0
  for marker in "$QUARANTINE_DIR"/*.prop; do [ -f "$marker" ] && count=$((count + 1)); done
  echo "$count"
}

restore_trial_active() { [ "$(get_prop state "$RESTORE_STATE")" = testing ] && [ -s "$RESTORE_TRIAL" ]; }

archive_restore_state() {
  local result="$1" reason="$2" stamp out tmp
  stamp=$(date '+%Y%m%dT%H%M%S' 2>/dev/null || date +%s); out="$RESTORE_HISTORY_DIR/$stamp-$result.prop"; tmp="$RESTORE_HISTORY_DIR/.restore-history.$$"
  { cat "$RESTORE_STATE" 2>/dev/null; printf 'result=%s
reason=%s
completed_at=%s
' "$result" "$(safe_manifest_value "$reason")" "$(date +%s 2>/dev/null || echo 0)"; [ -f "$RESTORE_TRIAL" ] && { printf '
modules:
'; cat "$RESTORE_TRIAL"; }; } > "$tmp"
  cat "$tmp" | atomic_write_file "$out" 600
  rm -f "$tmp"
}

_rollback_restore_trial_unlocked() {
  local reason="${1:-boot failed}" folder id name restored=0
  restore_trial_active || return 1
  while IFS="$(printf '\t')" read -r folder id name; do
    [ -n "$folder" ] || continue
    case "$folder" in ''|*/*|.|..|*[!A-Za-z0-9._-]*) continue ;; esac
    [ -d "$mdir/$folder" ] || continue
    touch "$mdir/$folder/disable" 2>/dev/null && restored=$((restored + 1))
  done < "$RESTORE_TRIAL"
  archive_restore_state failed "$reason"
  rm -f "$RESTORE_STATE" "$RESTORE_TRIAL"
  log "Restoration trial rolled back: $restored module(s) re-quarantined ($reason)."
  return 0
}

_complete_restore_trial_unlocked() {
  local folder id name completed=0 retained=0
  restore_trial_active || return 1
  while IFS="$(printf '\t')" read -r folder id name; do
    [ -n "$folder" ] || continue
    if [ ! -d "$mdir/$folder" ] || [ ! -f "$mdir/$folder/disable" ]; then
      rm -f "$QUARANTINE_DIR/$folder.prop"
      completed=$((completed + 1))
    else
      retained=$((retained + 1))
      log "Restored module remained disabled; quarantine ownership retained: $id [$folder]"
    fi
  done < "$RESTORE_TRIAL"
  archive_restore_state stable "stability window completed"
  rm -f "$RESTORE_STATE" "$RESTORE_TRIAL"
  log "Restoration trial succeeded: $completed released, $retained still quarantined."
  return 0
}

_restore_quarantined_modules_unlocked() {
  local mode="$1" value="$2" all_file selected_file count take folder id name trust rescue_id disabled_at tmp state_tmp restored=0
  restore_trial_active && { echo "A restoration trial is already active."; return 2; }
  all_file="$RESCUE_DIR/quarantine_all.$$"; selected_file="$RESCUE_DIR/quarantine_selected.$$"
  list_quarantined_modules_tsv > "$all_file"; count=$(wc -l < "$all_file" | tr -d ' ')
  [ "$count" -gt 0 ] 2>/dev/null || { rm -f "$all_file"; echo "No quarantined modules."; return 1; }
  case "$mode" in
    individual) awk -F '\t' -v token="$value" '$1==token || $2==token {print; exit}' "$all_file" > "$selected_file" ;;
    next|one) head -n 1 "$all_file" > "$selected_file" ;;
    batch) case "$value" in ''|*[!0-9]*) value=1 ;; esac; [ "$value" -lt 1 ] && value=1; head -n "$value" "$all_file" > "$selected_file" ;;
    half) take=$(((count + 1) / 2)); head -n "$take" "$all_file" > "$selected_file" ;;
    all) cp "$all_file" "$selected_file" ;;
    *) rm -f "$all_file"; echo "Invalid restore mode."; return 2 ;;
  esac
  rm -f "$all_file"
  [ -s "$selected_file" ] || { rm -f "$selected_file"; echo "Requested quarantined module was not found."; return 1; }
  tmp="$RESTORE_TRIAL.tmp.$$"; : > "$tmp"
  while IFS="$(printf '\t')" read -r folder id name trust rescue_id disabled_at; do
    [ -n "$folder" ] || continue
    case "$folder" in ''|*/*|.|..|*[!A-Za-z0-9._-]*) continue ;; esac
    [ -f "$QUARANTINE_DIR/$folder.prop" ] || continue
    if [ -d "$mdir/$folder" ]; then
      rm -f "$mdir/$folder/disable"
      printf '%s\t%s\t%s\n' "$folder" "$id" "$(safe_manifest_value "$name")" >> "$tmp"
      restored=$((restored + 1))
    fi
  done < "$selected_file"
  rm -f "$selected_file"
  [ "$restored" -gt 0 ] || { rm -f "$tmp"; echo "No available modules could be restored."; return 1; }
  chmod 600 "$tmp" 2>/dev/null; sync_path "$tmp"; mv -f "$tmp" "$RESTORE_TRIAL"
  state_tmp="$RESTORE_STATE.tmp.$$"
  { printf 'state=testing\nmode=%s\ncount=%s\nstarted_at=%s\nboot_id=%s\n' "$mode" "$restored" "$(date +%s 2>/dev/null || echo 0)" "$(current_boot_id)"; } > "$state_tmp" && chmod 600 "$state_tmp" 2>/dev/null && mv -f "$state_tmp" "$RESTORE_STATE"
  log "Restoration trial prepared: mode=$mode count=$restored. Reboot required; failed boot will automatically roll back."
  echo "Prepared restoration trial for $restored module(s). Reboot to test them."
}

list_quarantined_tsv() { list_quarantined_modules_tsv; }
restore_quarantined() { restore_quarantined_modules "$1" "$2"; }

mark_rescue_boot_stable() {
  local tmp="$RESCUE_DIR/last_stable.prop.tmp.$$"
  {
    printf 'boot_id=%s\n' "$(current_boot_id)"
    printf 'stable_at=%s\n' "$(date +%s 2>/dev/null || echo 0)"
    printf 'quarantined=%s\n' "$(quarantine_count)"
  } > "$tmp" || return 1
  cat "$tmp" | atomic_write_file "$RESCUE_DIR/last_stable.prop" 600
  rm -f "$tmp"
}

begin_boot_transaction() {
  local previous_state previous_id current_id
  previous_state=$(boot_state_value state); previous_id=$(boot_state_value boot_id); current_id=$(current_boot_id)
  case "$previous_state" in
    booting|monitoring)
      if [ -n "$previous_id" ] && [ "$previous_id" != "$current_id" ]; then
        log "Previous boot transaction was unfinished ($previous_state); retained loop strike for recovery."
        if rollback_restore_trial "unfinished boot transaction"; then
          modify_prop -s loops 0
          log "Restoration trial was rolled back before rescue escalation."
        fi
      fi
      ;;
  esac
  write_boot_state booting "post-fs-data started"
}

handle_boot_loop() {
  local loops_now disable_mode threshold_now stage reason="${1:-boot loop threshold reached}"
  loops_now=$(get_prop loops); disable_mode=$(get_prop disable); threshold_now=$(get_prop threshold); stage=$(get_prop rescue_stage)
  case "$loops_now" in ''|*[!0-9]*) loops_now=0 ;; esac
  case "$threshold_now" in ''|*[!0-9]*) threshold_now=2 ;; esac
  case "$stage" in ''|*[!0-9]*) stage=0 ;; esac
  log "Boot loops: $loops_now/$threshold_now | Rescue stage: $stage | Protection: $disable_mode"
  if [ "$loops_now" -ge "$threshold_now" ]; then
    case "$stage" in
      0) log "Threshold reached - targeted changed/suspect rescue"; rescue_targeted_modules "$reason" ;;
      1) log "Threshold reached - normal-module escalation"; rescue_normal_modules "$reason" ;;
      2) log "Threshold reached - trusted-module escalation"; rescue_trusted_modules "$reason" ;;
      *) log "Threshold reached - automatic rescue exhausted"; rescue_exhausted "$reason" ;;
    esac
  fi
  list_modules
}

trigger_crash_reboot() {
  local reason="${1:-stability failure}"
  log "Crash detected: $reason"
  mark_boot_state failed "$reason"
  if rollback_restore_trial "$reason"; then
    modify_prop -s loops 0
    log "Failed restoration trial handled without escalating unrelated modules."
  else
    handle_boot_loop "$reason"
  fi
  log "Rebooting device to continue bootloop protection..."
  reboot
  sleep 5
  exit 1
}


# --- AshReXcue 10.1: strict settings, atomic state, coordinated locks, dashboard/recovery ---
LOCK_ROOT="$ASHLOOPER_DIR/locks"
STATE_LOCK="$LOCK_ROOT/state.lock"
SETTINGS_REPAIR_LOG="$ASHLOOPER_DIR/settings_repairs.log"
ASH_STATE_LOCK_DEPTH=0

sync_path() {
  case "${ASH_TEST_NO_SYNC:-0}" in
    1|true|yes) return 0 ;;
  esac
  sync "$1" 2>/dev/null || sync 2>/dev/null || true
}

atomic_write_file() {
  local target="$1" mode="${2:-600}" parent tmp
  parent=${target%/*}; [ "$parent" = "$target" ] && parent=.
  mkdir -p "$parent" 2>/dev/null || return 1
  tmp="$target.tmp.$$.$(date +%s 2>/dev/null || echo 0)"
  cat > "$tmp" || { rm -f "$tmp"; return 1; }
  chmod "$mode" "$tmp" 2>/dev/null || { rm -f "$tmp"; return 1; }
  sync_path "$tmp"
  mv -f "$tmp" "$target" || { rm -f "$tmp"; return 1; }
}

atomic_write_text() {
  local target="$1" mode="$2" content="$3"
  printf '%s' "$content" | atomic_write_file "$target" "$mode"
}

atomic_copy_file() {
  local source="$1" target="$2" mode="${3:-600}"
  [ -f "$source" ] || return 1
  cat "$source" | atomic_write_file "$target" "$mode"
}

lock_owner_is_stale() {
  local lock_dir="$1" owner="$lock_dir/owner.prop" pid boot started now current_boot
  [ -d "$lock_dir" ] || return 0
  [ -L "$lock_dir" ] && return 0
  pid=$(get_prop pid "$owner"); boot=$(get_prop boot_id "$owner"); started=$(get_prop started_at "$owner")
  case "$pid" in ''|*[!0-9]*) return 0 ;; esac
  [ -d "/proc/$pid" ] || return 0
  current_boot=$(current_boot_id)
  [ -n "$boot" ] && [ "$boot" != "$current_boot" ] && return 0
  case "$started" in ''|*[!0-9]*) return 1 ;; esac
  now=$(date +%s 2>/dev/null || echo 0)
  [ "$now" -gt 0 ] && [ $((now - started)) -gt 900 ] && ! pid_cmdline_contains "$pid" "AshLooper" && return 0
  return 1
}

write_lock_owner() {
  local lock_dir="$1" name="$2"
  {
    printf 'pid=%s\n' "$$"
    printf 'boot_id=%s\n' "$(current_boot_id)"
    printf 'started_at=%s\n' "$(date +%s 2>/dev/null || echo 0)"
    printf 'name=%s\n' "$name"
  } > "$lock_dir/owner.prop"
  chmod 600 "$lock_dir/owner.prop" 2>/dev/null
}

state_lock_acquire() {
  local attempts=0
  case "$ASH_STATE_LOCK_DEPTH" in ''|*[!0-9]*) ASH_STATE_LOCK_DEPTH=0 ;; esac
  if [ "$ASH_STATE_LOCK_DEPTH" -gt 0 ]; then
    ASH_STATE_LOCK_DEPTH=$((ASH_STATE_LOCK_DEPTH + 1)); return 0
  fi
  mkdir -p "$LOCK_ROOT" 2>/dev/null || return 1
  chmod 700 "$LOCK_ROOT" 2>/dev/null
  while ! mkdir "$STATE_LOCK" 2>/dev/null; do
    if lock_owner_is_stale "$STATE_LOCK"; then
      rm -rf "$STATE_LOCK" 2>/dev/null
      continue
    fi
    attempts=$((attempts + 1))
    [ "$attempts" -ge 30 ] && return 1
    sleep 1
  done
  chmod 700 "$STATE_LOCK" 2>/dev/null
  write_lock_owner "$STATE_LOCK" state
  ASH_STATE_LOCK_DEPTH=1
  return 0
}

state_lock_release() {
  local depth owner_pid
  case "$ASH_STATE_LOCK_DEPTH" in ''|*[!0-9]*) ASH_STATE_LOCK_DEPTH=0; return 0 ;; esac
  [ "$ASH_STATE_LOCK_DEPTH" -gt 0 ] || return 0
  depth=$((ASH_STATE_LOCK_DEPTH - 1)); ASH_STATE_LOCK_DEPTH="$depth"
  [ "$depth" -gt 0 ] && return 0
  owner_pid=$(get_prop pid "$STATE_LOCK/owner.prop")
  [ "$owner_pid" = "$$" ] && rm -rf "$STATE_LOCK" 2>/dev/null
}

instance_lock_acquire() {
  local name="$1" lock_dir="$LOCK_ROOT/$1.lock" attempts=0
  mkdir -p "$LOCK_ROOT" 2>/dev/null || return 1
  chmod 700 "$LOCK_ROOT" 2>/dev/null
  while ! mkdir "$lock_dir" 2>/dev/null; do
    if lock_owner_is_stale "$lock_dir"; then rm -rf "$lock_dir" 2>/dev/null; continue; fi
    attempts=$((attempts + 1)); [ "$attempts" -ge 2 ] && return 1; sleep 1
  done
  chmod 700 "$lock_dir" 2>/dev/null
  write_lock_owner "$lock_dir" "$name"
  ASH_INSTANCE_LOCK="$lock_dir"
  return 0
}

instance_lock_release() {
  local lock_dir="${ASH_INSTANCE_LOCK:-}"
  [ -n "$lock_dir" ] || return 0
  [ "$(get_prop pid "$lock_dir/owner.prop")" = "$$" ] && rm -rf "$lock_dir" 2>/dev/null
  ASH_INSTANCE_LOCK=""
}

atomic_set_prop() {
  local key="$1" value="$2" target_file="$3" tmp mode rc
  case "$key" in ''|*[!A-Za-z0-9._-]*) return 1 ;; esac
  case "$value" in *'
'*) return 1 ;; esac
  state_lock_acquire || return 1
  [ -f "$target_file" ] || atomic_write_text "$target_file" 600 "" || { state_lock_release; return 1; }
  if [ "$target_file" = "$MODPATH/module.prop" ]; then mode=644; else mode=600; fi
  tmp="$target_file.edit.$$"
  awk -v k="$key" -v v="$value" '
    BEGIN { found=0 }
    index($0, k "=") == 1 { if (!found) print k "=" v; found=1; next }
    { print }
    END { if (!found) print k "=" v }
  ' "$target_file" > "$tmp" || { rm -f "$tmp"; state_lock_release; return 1; }
  cat "$tmp" | atomic_write_file "$target_file" "$mode"; rc=$?
  rm -f "$tmp"
  state_lock_release
  return "$rc"
}

record_setting_repair() {
  local key="$1" old="$2" new="$3" reason="$4" line
  SETTINGS_REPAIR_COUNT=$((SETTINGS_REPAIR_COUNT + 1))
  line="$(date +%s 2>/dev/null || echo 0)\t$key\t$(safe_manifest_value "$old")\t$(safe_manifest_value "$new")\t$(safe_manifest_value "$reason")"
  printf '%b\n' "$line" >> "$SETTINGS_REPAIR_LOG" 2>/dev/null
  log "Settings repair: $key '$old' -> '$new' ($reason)"
}

repair_setting() {
  local key="$1" value="$2" reason="$3" old
  old=$(get_prop "$key")
  [ "$old" = "$value" ] && return 0
  atomic_set_prop "$key" "$value" "$SETTINGS" || return 1
  record_setting_repair "$key" "$old" "$value" "$reason"
}

strict_int() {
  local key="$1" default="$2" min="$3" max="$4" value original
  original=$(get_prop "$key"); value="$original"
  case "$value" in ''|*[!0-9]*) value="$default" ;; esac
  [ "$value" -lt "$min" ] 2>/dev/null && value="$min"
  [ "$value" -gt "$max" ] 2>/dev/null && value="$max"
  repair_setting "$key" "$value" "integer range $min-$max"
  eval "$key=\"$value\""
}

strict_bool() {
  local key="$1" default="$2" value
  value=$(get_prop "$key"); case "$value" in true|false) ;; *) value="$default" ;; esac
  repair_setting "$key" "$value" "boolean"
  eval "$key=\"$value\""
}

strict_enum() {
  local key="$1" default="$2" allowed="$3" value token valid=0 old_ifs
  value=$(get_prop "$key"); old_ifs="$IFS"; IFS='|'
  for token in $allowed; do [ "$value" = "$token" ] && valid=1; done
  IFS="$old_ifs"; [ "$valid" -eq 1 ] || value="$default"
  repair_setting "$key" "$value" "allowed values: $allowed"
  eval "$key=\"$value\""
}

normalize_process_list() {
  local raw="$1" token output="" old_ifs
  raw=$(printf '%s' "$raw" | tr -d " '\"\r\n")
  old_ifs="$IFS"; IFS=','
  for token in $raw; do
    IFS="$old_ifs"
    case "$token" in ''|*[!A-Za-z0-9._:-]*) ;; *) case ",$output," in *",$token,"*) ;; *) [ -n "$output" ] && output="$output,$token" || output="$token" ;; esac ;; esac
    IFS=','
  done
  IFS="$old_ifs"; printf '%s' "$output"
}

list_without() {
  local raw="$1" excluded="$2" token output="" old_ifs
  old_ifs="$IFS"; IFS=','
  for token in $raw; do
    IFS="$old_ifs"; [ -n "$token" ] || { IFS=','; continue; }
    case ",$excluded," in *",$token,"*) ;; *) [ -n "$output" ] && output="$output,$token" || output="$token" ;; esac
    IFS=','
  done
  IFS="$old_ifs"; printf '%s' "$output"
}

rewrite_settings_canonical() {
  local tmp="$ASHLOOPER_DIR/settings.canonical.$$" key
  : > "$tmp"
  for key in mode loops disable rescue_stage timeout timeout_min timeout_max timeout_margin timeout_history_samples timeout_decrease_step threshold stability_time failure_threshold check_interval restart_limit boot_ready_consecutive systemui_process monitored_processes missing_process_action boot_animation_required ce_storage_required first_boot_grace ota_grace_time log install_date boot extra_stability whitelist protected_modules trusted_modules suspect_modules; do
    printf '%s=%s\n' "$key" "$(get_prop "$key")" >> "$tmp"
  done
  if ! cmp -s "$tmp" "$SETTINGS" 2>/dev/null; then
    cat "$tmp" | atomic_write_file "$SETTINGS" 600 || { rm -f "$tmp"; return 1; }
    record_setting_repair "settings.prop" "non-canonical" "canonical" "removed duplicates, malformed lines, and unknown keys"
  fi
  rm -f "$tmp"
}

validate_settings() {
  local legacy protected trusted suspect log_value lf lb date_value boot_value process_value systemui_value disable_expected
  initialize_storage
  mkdir -p "$LOCK_ROOT" 2>/dev/null; chmod 700 "$LOCK_ROOT" 2>/dev/null
  [ -f "$SETTINGS_REPAIR_LOG" ] || atomic_write_text "$SETTINGS_REPAIR_LOG" 600 ""
  SETTINGS_REPAIR_COUNT=0
  state_lock_acquire || return 1

  strict_enum mode 1 '1|2'
  strict_int loops 0 0 99
  strict_int rescue_stage 0 0 4
  case "$rescue_stage" in 0) disable_expected=none ;; 1) disable_expected=targeted ;; 2) disable_expected=normal ;; 3) disable_expected=trusted ;; 4) disable_expected=exhausted ;; esac
  repair_setting disable "$disable_expected" "derived from rescue_stage"
  disable="$disable_expected"

  strict_int timeout_min 60 45 300
  strict_int timeout_max 300 "$timeout_min" 900
  strict_int timeout 60 "$timeout_min" "$timeout_max"
  strict_int timeout_margin 30 10 180
  strict_int timeout_history_samples 5 3 10
  strict_int timeout_decrease_step 10 1 30
  strict_int threshold 2 1 5
  strict_int stability_time 60 30 600
  strict_int failure_threshold 3 1 10
  strict_int check_interval 3 1 30
  strict_int restart_limit 3 1 10
  strict_int boot_ready_consecutive 3 1 10
  strict_int first_boot_grace 90 0 600
  strict_int ota_grace_time 180 0 900
  strict_bool extra_stability false
  strict_bool boot_animation_required true
  strict_bool ce_storage_required true
  strict_enum missing_process_action rescue 'warn|rescue'

  systemui_value=$(get_prop systemui_process)
  case "$systemui_value" in ''|*[!A-Za-z0-9._:-]*) systemui_value=com.android.systemui ;; esac
  repair_setting systemui_process "$systemui_value" "safe process identifier"; systemui_process="$systemui_value"
  process_value=$(normalize_process_list "$(get_prop monitored_processes)")
  repair_setting monitored_processes "$process_value" "comma-separated process identifiers"; monitored_processes="$process_value"

  log_value=$(get_prop log); lf=${log_value%%-*}; lb=${log_value#*-}
  case "$lf" in ''|*[!0-9]*) lf=1 ;; esac; case "$lb" in ''|*[!0-9]*) lb=0 ;; esac
  [ "$lf" -lt 1 ] 2>/dev/null && lf=1; [ "$lf" -gt 10 ] 2>/dev/null && lf=10
  [ "$lb" -lt 0 ] 2>/dev/null && lb=0; [ "$lb" -gt 10 ] 2>/dev/null && lb=10
  repair_setting log "$lf-$lb" "log rotation tuple"

  date_value=$(get_prop install_date)
  case "$date_value" in none|unknown) ;; ????-??-??) ;; *) date_value=none ;; esac
  repair_setting install_date "$date_value" "date or none"
  boot_value=$(get_prop boot)
  case "$boot_value" in none|booting|monitoring|stable|failed) ;; *) boot_value=none ;; esac
  repair_setting boot "$boot_value" "boot transaction state"

  legacy="$(get_prop protected_modules),$(get_prop whitelist)"
  protected=$(normalize_module_list_value "$legacy" AshLooper)
  trusted=$(normalize_module_list_value "$(get_prop trusted_modules)" "")
  trusted=$(list_without "$trusted" "$protected")
  suspect=$(normalize_module_list_value "$(get_prop suspect_modules)" "")
  suspect=$(list_without "$suspect" "$protected,$trusted")
  repair_setting protected_modules "\"$protected\"" "normalized protected list"
  repair_setting whitelist "\"$protected\"" "legacy protected-list mirror"
  repair_setting trusted_modules "\"$trusted\"" "normalized non-overlapping trusted list"
  repair_setting suspect_modules "\"$suspect\"" "normalized non-overlapping suspect list"
  protected_modules="$protected"; whitelist="$protected"; trusted_modules="$trusted"; suspect_modules="$suspect"

  rewrite_settings_canonical
  state_lock_release
  [ "$SETTINGS_REPAIR_COUNT" -gt 0 ] && log "Settings validation repaired $SETTINGS_REPAIR_COUNT item(s)."
  return 0
}

settings_key_allowed() {
  case "$1" in mode|timeout|timeout_min|timeout_max|timeout_margin|timeout_history_samples|timeout_decrease_step|threshold|stability_time|failure_threshold|check_interval|restart_limit|boot_ready_consecutive|systemui_process|monitored_processes|missing_process_action|boot_animation_required|ce_storage_required|launcher_check_required|launcher_wait|launcher_focus_required|first_boot_grace|ota_grace_time|extra_stability|whitelist|protected_modules|trusted_modules|suspect_modules) return 0 ;; esac|fail_boot_file_required|fail_boot_file_path|fail_boot_file_wait
  return 1
}

settings_set() {
  local key="$1" value="$2" rc
  settings_key_allowed "$key" || { echo "Setting is not editable: $key"; return 2; }
  case "$value" in *'
'*) echo "Multiline values are not allowed."; return 2 ;; esac
  state_lock_acquire || { echo "Settings are busy."; return 3; }
  atomic_set_prop "$key" "$value" "$SETTINGS" || { state_lock_release; echo "Failed to write setting."; return 1; }
  [ "$key" = whitelist ] && atomic_set_prop protected_modules "$value" "$SETTINGS"
  [ "$key" = protected_modules ] && atomic_set_prop whitelist "$value" "$SETTINGS"
  validate_settings; rc=$?
  state_lock_release
  [ "$rc" -eq 0 ] && echo "$key saved."
  return "$rc"
}

settings_batch_set() {
  local rc=0 key value
  [ $(( $# % 2 )) -eq 0 ] || { echo "Expected key/value pairs."; return 2; }
  state_lock_acquire || { echo "Settings are busy."; return 3; }
  while [ "$#" -gt 0 ]; do
    key="$1"; value="$2"; shift 2
    settings_key_allowed "$key" || { echo "Setting is not editable: $key"; rc=2; break; }
    case "$value" in *'\n'*) echo "Multiline values are not allowed."; rc=2; break ;; esac
    atomic_set_prop "$key" "$value" "$SETTINGS" || { rc=1; break; }
    [ "$key" = whitelist ] && atomic_set_prop protected_modules "$value" "$SETTINGS"
    [ "$key" = protected_modules ] && atomic_set_prop whitelist "$value" "$SETTINGS"
  done
  if [ "$rc" -eq 0 ]; then validate_settings; rc=$?; fi
  state_lock_release
  [ "$rc" -eq 0 ] && echo "Settings saved and validated."
  return "$rc"
}

module_prop_set() {
  local key="$1" value="$2"
  case "$key" in description) ;; *) echo "Module property is not editable."; return 2 ;; esac
  atomic_set_prop "$key" "$value" "$MODPATH/module.prop"
}

write_boot_state() {
  local state="$1" reason="$2" boot_id started loops_now disable_now
  boot_id=$(current_boot_id); started=$(boot_state_value started_at); [ -n "$started" ] || started=$(date +%s 2>/dev/null || echo 0)
  loops_now=$(get_prop loops); disable_now=$(get_prop disable)
  {
    printf 'boot_id=%s\n' "$boot_id"
    printf 'state=%s\n' "$state"
    printf 'started_at=%s\n' "$started"
    printf 'updated_at=%s\n' "$(date +%s 2>/dev/null || echo 0)"
    printf 'loops=%s\n' "${loops_now:-0}"
    printf 'disable=%s\n' "${disable_now:-none}"
    printf 'reason=%s\n' "$(safe_manifest_value "$reason")"
  } | atomic_write_file "$BOOT_STATE" 600 || return 1
  modify_prop -s boot "$state" "$SETTINGS"
}

save_stable_build() {
  current_build_identity | atomic_write_file "$BUILD_STATE" 600
}

update_adaptive_timeout() {
  local elapsed="$1" history_tmp samples median desired current sorted count mid a b
  state_lock_acquire || return 1
  history_tmp="$BOOT_HISTORY.build.$$"
  { cat "$BOOT_HISTORY" 2>/dev/null; printf '%s\n' "$elapsed"; } | grep -E '^[0-9]+$' | tail -n "$timeout_history_samples" > "$history_tmp"
  cat "$history_tmp" | atomic_write_file "$BOOT_HISTORY" 600 || { rm -f "$history_tmp"; state_lock_release; return 1; }
  rm -f "$history_tmp"
  sorted="$BOOT_HISTORY.sorted.$$"; sort -n "$BOOT_HISTORY" > "$sorted"
  count=$(wc -l < "$sorted" | tr -d ' ')
  if [ "$count" -le 0 ]; then median="$elapsed"; elif [ $((count % 2)) -eq 1 ]; then mid=$(((count + 1) / 2)); median=$(sed -n "${mid}p" "$sorted"); else mid=$((count / 2)); a=$(sed -n "${mid}p" "$sorted"); b=$(sed -n "$((mid + 1))p" "$sorted"); median=$(((a + b) / 2)); fi
  rm -f "$sorted"
  desired=$((median + timeout_margin)); current=$(get_prop timeout)
  case "$current" in ''|*[!0-9]*) current="$timeout_min" ;; esac
  [ "$desired" -lt "$timeout_min" ] && desired="$timeout_min"; [ "$desired" -gt "$timeout_max" ] && desired="$timeout_max"
  if [ "$desired" -lt "$current" ] && [ $((current - desired)) -gt "$timeout_decrease_step" ]; then desired=$((current - timeout_decrease_step)); fi
  [ "$desired" -lt "$timeout_min" ] && desired="$timeout_min"
  modify_prop timeout "$desired"
  ADAPTIVE_TIMEOUT="$desired"; ADAPTIVE_MEDIAN="$median"
  state_lock_release
}

write_owned_pid_file() {
  local pid_file="$1" pid="$2" marker="$3"
  case "$pid" in ''|*[!0-9]*) return 1 ;; esac
  {
    printf 'pid=%s\n' "$pid"
    printf 'boot_id=%s\n' "$(current_boot_id)"
    printf 'marker=%s\n' "$(safe_manifest_value "$marker")"
    printf 'started_at=%s\n' "$(date +%s 2>/dev/null || echo 0)"
  } | atomic_write_file "$pid_file" 600
}

owned_pid_value() {
  local pid_file="$1" pid
  pid=$(get_prop pid "$pid_file")
  [ -n "$pid" ] || pid=$(cat "$pid_file" 2>/dev/null | tr -d '[:space:]')
  printf '%s' "$pid"
}

stop_owned_pid() {
  local pid_file="$1" marker="$2" pid recorded_marker boot current_boot
  [ -f "$pid_file" ] || return 0
  pid=$(owned_pid_value "$pid_file"); recorded_marker=$(get_prop marker "$pid_file"); boot=$(get_prop boot_id "$pid_file"); current_boot=$(current_boot_id)
  case "$pid" in ''|*[!0-9]*) rm -f "$pid_file"; return 0 ;; esac
  [ -n "$boot" ] && [ "$boot" != "$current_boot" ] && { rm -f "$pid_file"; return 0; }
  [ -n "$recorded_marker" ] && [ "$recorded_marker" != "$marker" ] && { rm -f "$pid_file"; return 1; }
  if [ "$pid" != "$$" ] && pid_cmdline_contains "$pid" "$marker"; then
    kill "$pid" 2>/dev/null; sleep 1
    pid_cmdline_contains "$pid" "$marker" && kill -9 "$pid" 2>/dev/null
  fi
  rm -f "$pid_file"
}



safe_remove_persistent_state() {
  [ "$ASHLOOPER_DIR" = /data/adb/ashlooper ] || return 1
  [ -L "$ASHLOOPER_DIR" ] && { rm -f "$ASHLOOPER_DIR"; return 0; }
  rm -rf "$ASHLOOPER_DIR"
}

# Lock trust and restoration mutations as complete operations.

list_rescue_history_tsv() {
  local limit="${1:-10}" file count=0
  case "$limit" in ''|*[!0-9]*) limit=10 ;; esac
  for file in $(ls -1t "$RESCUE_HISTORY_DIR"/*.json 2>/dev/null); do
    [ -f "$file" ] || continue
    if [ -x "$JQ" ]; then
      "$JQ" -r '[.rescueId,.createdAt,.stage,.status,.disabledCount,.reason] | @tsv' "$file" 2>/dev/null
    else
      printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$(basename "$file" .json)" "0" "unknown" "unknown" "0" "$(basename "$file")"
    fi
    count=$((count + 1)); [ "$count" -ge "$limit" ] && break
  done
}

list_restore_history_tsv() {
  local limit="${1:-10}" file count=0
  case "$limit" in ''|*[!0-9]*) limit=10 ;; esac
  for file in $(ls -1t "$RESTORE_HISTORY_DIR"/*.prop 2>/dev/null); do
    [ -f "$file" ] || continue
    printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$(basename "$file")" "$(get_prop completed_at "$file")" "$(get_prop result "$file")" "$(get_prop mode "$file")" "$(get_prop count "$file")" "$(get_prop reason "$file")"
    count=$((count + 1)); [ "$count" -ge "$limit" ] && break
  done
}

forget_stale_quarantine() {
  local token="$1" marker folder id
  case "$token" in ''|*[!A-Za-z0-9._-]*) echo "Invalid module identifier."; return 2 ;; esac
  state_lock_acquire || return 3
  for marker in "$QUARANTINE_DIR"/*.prop; do
    [ -f "$marker" ] || continue
    folder=$(get_prop folder "$marker"); id=$(get_prop id "$marker")
    [ "$token" = "$folder" ] || [ "$token" = "$id" ] || continue
    if [ ! -d "$mdir/$folder" ] || [ ! -f "$mdir/$folder/disable" ]; then
      rm -f "$marker"; state_lock_release; echo "Removed stale quarantine record for $id."; return 0
    fi
    state_lock_release; echo "Module is still disabled; restore it instead of forgetting ownership."; return 2
  done
  state_lock_release; echo "Quarantine record not found."; return 1
}

cancel_restore_trial() {
  rollback_restore_trial "cancelled manually from Recovery Center"
}

stage_label() {
  case "$1" in 0) echo targeted ;; 1) echo normal ;; 2) echo trusted ;; 3|4) echo manual-recovery ;; *) echo unknown ;; esac
}

next_rescue_label() {
  case "$1" in 0) echo 'Changed and suspect modules' ;; 1) echo 'Normal modules' ;; 2) echo 'Trusted modules' ;; 3|4) echo 'Manual recovery required' ;; *) echo 'Unknown' ;; esac
}

dashboard_json() {
  local state reason started updated loops_now threshold_now stage timeout_now timeout_min_now timeout_max_now stability quarantine restore_state restore_count enabled=0 disabled=0 protected=0 trusted=0 suspect=0 module_folder trust status latest_id latest_stage latest_status latest_count latest_reason repairs last_repair
  # Dashboard reads are observational. All state writers use atomic replacement, so the
  # companion must not wait behind the boot transaction lock just to render status.
  initialize_storage
  state=$(get_prop state "$BOOT_STATE" 2>/dev/null || true); [ -n "$state" ] || state=$(get_prop boot 2>/dev/null || true); [ -n "$state" ] || state=none
  reason=$(get_prop reason "$BOOT_STATE" 2>/dev/null || true); started=$(get_prop started_at "$BOOT_STATE" 2>/dev/null || true); updated=$(get_prop updated_at "$BOOT_STATE" 2>/dev/null || true)
  loops_now=$(get_prop loops 2>/dev/null || true); threshold_now=$(get_prop threshold 2>/dev/null || true); stage=$(get_prop rescue_stage 2>/dev/null || true)
  timeout_now=$(get_prop timeout 2>/dev/null || true); timeout_min_now=$(get_prop timeout_min 2>/dev/null || true); timeout_max_now=$(get_prop timeout_max 2>/dev/null || true); stability=$(get_prop stability_time 2>/dev/null || true)
  quarantine=$(quarantine_count); restore_state=$(get_prop state "$RESTORE_STATE" 2>/dev/null || true); restore_count=$(get_prop count "$RESTORE_STATE" 2>/dev/null || true)
  [ -n "$restore_state" ] || restore_state=idle; [ -n "$restore_count" ] || restore_count=0
  for module_folder in "$mdir"/*; do
    [ -d "$module_folder" ] || continue
    module_identity "$module_folder"; trust=$(base_module_trust "$MODULE_ID" "$MODULE_FOLDER")
    [ -f "$module_folder/disable" ] && disabled=$((disabled + 1)) || enabled=$((enabled + 1))
    case "$trust" in protected) protected=$((protected + 1)) ;; trusted) trusted=$((trusted + 1)) ;; suspect) suspect=$((suspect + 1)) ;; esac
  done
  if [ -f "$LATEST_RESCUE" ] && [ -x "$JQ" ]; then
    latest_id=$("$JQ" -r '.rescueId // ""' "$LATEST_RESCUE" 2>/dev/null); latest_stage=$("$JQ" -r '.stage // ""' "$LATEST_RESCUE" 2>/dev/null)
    latest_status=$("$JQ" -r '.status // ""' "$LATEST_RESCUE" 2>/dev/null); latest_count=$("$JQ" -r '.disabledCount // 0' "$LATEST_RESCUE" 2>/dev/null); latest_reason=$("$JQ" -r '.reason // ""' "$LATEST_RESCUE" 2>/dev/null)
  fi
  if [ -f "$SETTINGS_REPAIR_LOG" ]; then
    repairs=$(wc -l < "$SETTINGS_REPAIR_LOG" 2>/dev/null | tr -d ' '); [ -n "$repairs" ] || repairs=0
    last_repair=$(tail -n 1 "$SETTINGS_REPAIR_LOG" 2>/dev/null)
  else
    repairs=0
    last_repair=""
  fi
  cat <<EOF
{
  "module":{"version":"$(json_escape "$(get_prop version "$MODPATH/module.prop")")","versionCode":$(get_prop versionCode "$MODPATH/module.prop"),"root":"$(json_escape "$method")"},
  "boot":{"state":"$(json_escape "$state")","reason":"$(json_escape "$reason")","startedAt":${started:-0},"updatedAt":${updated:-0},"loops":${loops_now:-0},"threshold":${threshold_now:-0}},
  "rescue":{"stage":${stage:-0},"stageLabel":"$(json_escape "$(stage_label "$stage")")","next":"$(json_escape "$(next_rescue_label "$stage")")","quarantined":${quarantine:-0},"restoreState":"$(json_escape "$restore_state")","restoreCount":${restore_count:-0}},
  "timing":{"timeout":${timeout_now:-0},"minimum":${timeout_min_now:-0},"maximum":${timeout_max_now:-0},"stability":${stability:-0}},
  "modules":{"enabled":$enabled,"disabled":$disabled,"protected":$protected,"trusted":$trusted,"suspect":$suspect},
  "latestRescue":{"id":"$(json_escape "$latest_id")","stage":"$(json_escape "$latest_stage")","status":"$(json_escape "$latest_status")","count":${latest_count:-0},"reason":"$(json_escape "$latest_reason")"},
  "settings":{"repairCount":${repairs:-0},"lastRepair":"$(json_escape "$last_repair")"}
}
EOF
}


set_module_trust() {
  local rc
  state_lock_acquire || { echo "Recovery state is busy."; return 3; }
  _set_module_trust_unlocked "$@"; rc=$?
  state_lock_release; return "$rc"
}
rollback_restore_trial() {
  local rc
  state_lock_acquire || return 3
  _rollback_restore_trial_unlocked "$@"; rc=$?
  state_lock_release; return "$rc"
}
complete_restore_trial() {
  local rc
  state_lock_acquire || return 3
  _complete_restore_trial_unlocked "$@"; rc=$?
  state_lock_release; return "$rc"
}
restore_quarantined_modules() {
  local rc
  state_lock_acquire || { echo "Recovery state is busy."; return 3; }
  _restore_quarantined_modules_unlocked "$@"; rc=$?
  state_lock_release; return "$rc"
}
restore_quarantined() { restore_quarantined_modules "$1" "$2"; }


list_quarantined_modules_tsv() {
  local marker folder id name trust rescue_id disabled_at exists disable_present
  for marker in "$QUARANTINE_DIR"/*.prop; do
    [ -f "$marker" ] || continue
    folder=$(get_prop folder "$marker"); id=$(get_prop id "$marker"); name=$(get_prop name "$marker")
    trust=$(get_prop base_trust "$marker"); rescue_id=$(get_prop rescue_id "$marker"); disabled_at=$(get_prop disabled_at "$marker")
    [ -d "$mdir/$folder" ] && exists=true || exists=false
    [ -f "$mdir/$folder/disable" ] && disable_present=true || disable_present=false
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$folder" "$id" "$name" "$trust" "$rescue_id" "$disabled_at" "$exists" "$disable_present"
  done | sort
}
list_quarantined_tsv() { list_quarantined_modules_tsv; }


# --- AshReXcue 10.2: history, diagnostics, queued settings, module filtering support ---
PENDING_SETTINGS="$ASHLOOPER_DIR/pending-settings.prop"
SETTINGS_CHANGE_LOG="$ASHLOOPER_DIR/settings_changes.tsv"
APPLIED_SETTINGS_DIR="$ASHLOOPER_DIR/applied-settings"
DIAGNOSTICS_DIR="$ASHLOOPER_DIR/diagnostics"
DIAGNOSTICS_DOWNLOAD_DIR="${ASH_DIAGNOSTICS_DOWNLOAD_DIR:-/sdcard/Download}"

initialize_storage() {
  mkdir -p "$ASHLOOPER_DIR" "$LOG_DIR" "$RESCUE_HISTORY_DIR" "$QUARANTINE_DIR" "$RESTORE_HISTORY_DIR" "$APPLIED_SETTINGS_DIR" "$DIAGNOSTICS_DIR" 2>/dev/null
  chmod 700 "$ASHLOOPER_DIR" "$LOG_DIR" "$RESCUE_DIR" "$RESCUE_HISTORY_DIR" "$QUARANTINE_DIR" "$RESTORE_HISTORY_DIR" "$APPLIED_SETTINGS_DIR" "$DIAGNOSTICS_DIR" 2>/dev/null
  [ -f "$SETTINGS_CHANGE_LOG" ] || atomic_write_text "$SETTINGS_CHANGE_LOG" 600 ""
  if [ -d "$LEGACY_LOG_DIR" ] && [ ! -f "$LOG_DIR/.legacy_migrated" ]; then
    for legacy_log in "$LEGACY_LOG_DIR"/AshReXcueSession-*.log "$LEGACY_LOG_DIR"/looperbug.log; do
      [ -f "$legacy_log" ] || continue
      legacy_name=$(basename "$legacy_log")
      [ -f "$LOG_DIR/$legacy_name" ] || cp -p "$legacy_log" "$LOG_DIR/$legacy_name" 2>/dev/null
    done
    atomic_write_text "$LOG_DIR/.legacy_migrated" 600 "migrated
" || true
  fi
}

settings_queue_active() {
  case "$(get_prop state "$BOOT_STATE" 2>/dev/null)" in booting|monitoring) return 0 ;; esac
  return 1
}

setting_normalize_value() {
  local key="$1" value="$2" min max normalized
  NORMALIZED_SETTING_VALUE=""
  case "$value" in *'
'*) return 1 ;; esac
  case "$key" in
    mode) case "$value" in 1|2) ;; *) return 1 ;; esac ;;
    timeout_min) min=45; max=300 ;;
    timeout_max) min=60; max=900 ;;
    timeout|threshold|stability_time|failure_threshold|check_interval|restart_limit|boot_ready_consecutive|timeout_margin|timeout_history_samples|timeout_decrease_step|first_boot_grace|ota_grace_time)
      case "$key" in
        timeout) min=45; max=900 ;;
        threshold) min=1; max=5 ;;
        stability_time) min=30; max=600 ;;
        failure_threshold|restart_limit|boot_ready_consecutive) min=1; max=10 ;;
        check_interval) min=1; max=30 ;;
        timeout_margin) min=10; max=180 ;;
        timeout_history_samples) min=3; max=10 ;;
        timeout_decrease_step) min=1; max=30 ;;
        first_boot_grace) min=0; max=600 ;;
        ota_grace_time) min=0; max=900 ;;
      esac ;;
    extra_stability|boot_animation_required|ce_storage_required) case "$value" in true|false) ;; *) return 1 ;; esac ;;
    missing_process_action) case "$value" in warn|rescue) ;; *) return 1 ;; esac ;;
    systemui_process) case "$value" in ''|*[!A-Za-z0-9._:-]*) return 1 ;; esac ;;
    monitored_processes) value=$(normalize_process_list "$value") ;;
    whitelist|protected_modules)
      value="\"$(normalize_module_list_value "$value" AshLooper)\"" ;;
    trusted_modules|suspect_modules)
      value="\"$(normalize_module_list_value "$value" "")\"" ;;
    *) return 1 ;;
  esac
  if [ -n "$min" ]; then
    case "$value" in ''|*[!0-9]*) return 1 ;; esac
    [ "$value" -ge "$min" ] 2>/dev/null && [ "$value" -le "$max" ] 2>/dev/null || return 1
  fi
  NORMALIZED_SETTING_VALUE="$value"
  return 0
}

record_settings_change() {
  local source="$1" key="$2" old="$3" new="$4" phase
  phase=$(get_prop state "$BOOT_STATE" 2>/dev/null || true); [ -n "$phase" ] || phase=none
  printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$(date +%s 2>/dev/null || echo 0)" "$source" "$key" "$(safe_manifest_value "$old")" "$(safe_manifest_value "$new")" "$phase" >> "$SETTINGS_CHANGE_LOG" 2>/dev/null
}

_write_setting_unlocked() {
  local key="$1" value="$2" old
  old=$(get_prop "$key")
  atomic_set_prop "$key" "$value" "$SETTINGS" || return 1
  if [ "$key" = whitelist ]; then atomic_set_prop protected_modules "$value" "$SETTINGS" || return 1; fi
  if [ "$key" = protected_modules ]; then atomic_set_prop whitelist "$value" "$SETTINGS" || return 1; fi
  record_settings_change applied "$key" "$old" "$value"
}

_queue_setting_unlocked() {
  local key="$1" value="$2" old
  [ -f "$PENDING_SETTINGS" ] || atomic_write_text "$PENDING_SETTINGS" 600 "" || return 1
  old=$(get_prop "$key" "$PENDING_SETTINGS" 2>/dev/null); [ -n "$old" ] || old=$(get_prop "$key")
  atomic_set_prop "$key" "$value" "$PENDING_SETTINGS" || return 1
  if [ "$key" = whitelist ]; then atomic_set_prop protected_modules "$value" "$PENDING_SETTINGS" || return 1; fi
  if [ "$key" = protected_modules ]; then atomic_set_prop whitelist "$value" "$PENDING_SETTINGS" || return 1; fi
  record_settings_change queued "$key" "$old" "$value"
}

settings_set() {
  local key="$1" value="$2" rc active=0
  settings_key_allowed "$key" || { echo "Setting is not editable: $key"; return 2; }
  setting_normalize_value "$key" "$value" || { echo "Invalid value for $key."; return 2; }
  value="$NORMALIZED_SETTING_VALUE"
  state_lock_acquire || { echo "Settings are busy."; return 3; }
  settings_queue_active && active=1
  if [ "$active" -eq 1 ]; then
    _queue_setting_unlocked "$key" "$value"; rc=$?
  else
    _write_setting_unlocked "$key" "$value"; rc=$?
    [ "$rc" -eq 0 ] && validate_settings >/dev/null 2>&1; rc=$?
  fi
  state_lock_release
  [ "$rc" -eq 0 ] || return "$rc"
  if [ "$active" -eq 1 ]; then echo "$key queued for the next boot."; else echo "$key saved and validated."; fi
}

settings_batch_set() {
  local rc=0 key value active=0 count=0 batch="$ASHLOOPER_DIR/.settings-batch.$$"
  [ $(( $# % 2 )) -eq 0 ] || { echo "Expected key/value pairs."; return 2; }
  : > "$batch" || return 1
  while [ "$#" -gt 0 ]; do
    key="$1"; value="$2"; shift 2
    settings_key_allowed "$key" || { echo "Setting is not editable: $key"; rm -f "$batch"; return 2; }
    setting_normalize_value "$key" "$value" || { echo "Invalid value for $key."; rm -f "$batch"; return 2; }
    printf '%s\t%s\n' "$key" "$NORMALIZED_SETTING_VALUE" >> "$batch"
    count=$((count + 1))
  done
  state_lock_acquire || { rm -f "$batch"; echo "Settings are busy."; return 3; }
  settings_queue_active && active=1
  while IFS="$(printf '\t')" read -r key value; do
    [ -n "$key" ] || continue
    if [ "$active" -eq 1 ]; then _queue_setting_unlocked "$key" "$value" || { rc=1; break; }
    else _write_setting_unlocked "$key" "$value" || { rc=1; break; }
    fi
  done < "$batch"
  rm -f "$batch"
  if [ "$rc" -eq 0 ] && [ "$active" -eq 0 ]; then validate_settings >/dev/null 2>&1; rc=$?; fi
  state_lock_release
  [ "$rc" -eq 0 ] || { echo "Failed to update settings."; return "$rc"; }
  if [ "$active" -eq 1 ]; then echo "$count setting(s) queued for the next boot."; else echo "$count setting(s) saved and validated."; fi
}

pending_settings_count() {
  [ -s "$PENDING_SETTINGS" ] || { echo 0; return; }
  grep -c '^[A-Za-z0-9._-][A-Za-z0-9._-]*=' "$PENDING_SETTINGS" 2>/dev/null || echo 0
}

pending_settings_json() {
  local first=1 key value count phase
  count=$(pending_settings_count); phase=$(get_prop state "$BOOT_STATE" 2>/dev/null || true); [ -n "$phase" ] || phase=none
  printf '{"count":%s,"bootPhase":"%s","willApplyAt":"next post-fs-data","items":[' "$count" "$(json_escape "$phase")"
  if [ -f "$PENDING_SETTINGS" ]; then
    while IFS='=' read -r key value; do
      settings_key_allowed "$key" || continue
      [ "$first" -eq 0 ] && printf ','
      printf '{"key":"%s","value":"%s","current":"%s"}' "$(json_escape "$key")" "$(json_escape "$value")" "$(json_escape "$(get_prop "$key")")"
      first=0
    done < "$PENDING_SETTINGS"
  fi
  printf ']}\n'
}

discard_pending_settings() {
  state_lock_acquire || { echo "Settings are busy."; return 3; }
  if [ -s "$PENDING_SETTINGS" ]; then
    count=$(pending_settings_count)
    rm -f "$PENDING_SETTINGS"
    record_settings_change discarded pending "$count setting(s)" none
    state_lock_release
    echo "Discarded $count queued setting(s)."
  else
    state_lock_release
    echo "No queued settings."
  fi
}

apply_pending_settings() {
  local key value rc=0 count=0 stamp archive
  initialize_storage
  [ -s "$PENDING_SETTINGS" ] || return 0
  state_lock_acquire || { log "Pending settings could not acquire state lock."; return 1; }
  stamp=$(date '+%Y%m%dT%H%M%S' 2>/dev/null || date +%s)
  archive="$APPLIED_SETTINGS_DIR/$stamp.prop"
  atomic_copy_file "$PENDING_SETTINGS" "$archive" 600 || true
  while IFS='=' read -r key value; do
    settings_key_allowed "$key" || continue
    setting_normalize_value "$key" "$value" || { log "Skipped invalid queued setting: $key"; continue; }
    _write_setting_unlocked "$key" "$NORMALIZED_SETTING_VALUE" || { rc=1; break; }
    count=$((count + 1))
  done < "$PENDING_SETTINGS"
  if [ "$rc" -eq 0 ]; then
    rm -f "$PENDING_SETTINGS"
    validate_settings >/dev/null 2>&1 || rc=$?
  fi
  state_lock_release
  if [ "$rc" -eq 0 ]; then log "Applied $count queued setting(s) at post-fs-data."; else log "Failed while applying queued settings; queue retained."; fi
  return "$rc"
}

rescue_manifest_json() {
  local rescue_id="$1" file
  case "$rescue_id" in ''|*[!A-Za-z0-9._-]*) echo '{"error":"invalid rescue id"}'; return 2 ;; esac
  file="$RESCUE_HISTORY_DIR/$rescue_id.json"
  [ -f "$file" ] || { echo '{"error":"rescue manifest not found"}'; return 1; }
  if [ -x "$JQ" ]; then "$JQ" -c '.' "$file" 2>/dev/null; else cat "$file"; fi
}

rescue_history_json() {
  local limit="${1:-10}" offset="${2:-0}" status="${3:-all}" stage="${4:-all}" query="${5:-}" file
  case "$limit" in ''|*[!0-9]*) limit=10 ;; esac; [ "$limit" -gt 50 ] && limit=50; [ "$limit" -lt 1 ] && limit=1
  case "$offset" in ''|*[!0-9]*) offset=0 ;; esac
  case "$status" in all|quarantined|empty|failed|stable|cancelled) ;; *) status=all ;; esac
  case "$stage" in all|targeted|normal|trusted|manual-recovery|exhausted) ;; *) stage=all ;; esac
  set --
  for file in "$RESCUE_HISTORY_DIR"/*.json; do [ -f "$file" ] && set -- "$@" "$file"; done
  if [ "$#" -eq 0 ]; then printf '{"total":0,"offset":%s,"limit":%s,"items":[]}\n' "$offset" "$limit"; return 0; fi
  [ -x "$JQ" ] || { printf '{"total":0,"offset":%s,"limit":%s,"items":[]}\n' "$offset" "$limit"; return 0; }
  "$JQ" -s --arg status "$status" --arg stage "$stage" --arg query "$query" --argjson offset "$offset" --argjson limit "$limit" '
    map(select(type=="object")) | sort_by(.createdAt // 0) | reverse |
    map(select(($status=="all" or (.status // "")==$status) and ($stage=="all" or (.stage // "")==$stage) and
      (($query|length)==0 or (((.rescueId // "")+" "+(.reason // "")+" "+([.modules[]?.id,.modules[]?.name,.modules[]?.folder]|join(" ")))|ascii_downcase|contains($query|ascii_downcase))))) as $items |
    {total:($items|length),offset:$offset,limit:$limit,items:($items[$offset:($offset+$limit)] | map({schema,rescueId,bootId,createdAt,stage,reason,status,disabledCount,modules}))}
  ' "$@" 2>/dev/null
}

sanitize_diagnostic_stream() {
  awk '
    { low=tolower($0) }
    low ~ /(x-ash-token|authorization:|uplink_key|session[_ -]?token|android_id|serialno|ro.serial|imei|meid)/ { print "[REDACTED sensitive line]"; next }
    { print }
  '
}

diagnostic_copy_text() {
  local source="$1" target="$2" lines="${3:-0}"
  [ -f "$source" ] || return 0
  mkdir -p "${target%/*}" 2>/dev/null || return 1
  if [ "$lines" -gt 0 ] 2>/dev/null; then tail -n "$lines" "$source" 2>/dev/null | sanitize_diagnostic_stream > "$target"
  else sanitize_diagnostic_stream < "$source" > "$target"
  fi
  chmod 600 "$target" 2>/dev/null
}

list_diagnostic_exports_tsv() {
  local file count=0 limit="${1:-5}" size mtime
  case "$limit" in ''|*[!0-9]*) limit=5 ;; esac
  for file in $(ls -1t "$DIAGNOSTICS_DIR"/AshReXcue-diagnostics-* 2>/dev/null); do
    [ -f "$file" ] || continue
    size=$(wc -c < "$file" 2>/dev/null | tr -d ' '); mtime=$(date -r "$file" +%s 2>/dev/null || echo 0)
    printf '%s\t%s\t%s\n' "$file" "${size:-0}" "$mtime"
    count=$((count + 1)); [ "$count" -ge "$limit" ] && break
  done
}

create_diagnostic_export() {
  local stamp work state_archive final_archive output tar_bin bb file copied=0
  initialize_storage
  stamp=$(date '+%Y%m%d-%H%M%S' 2>/dev/null || date +%s)
  work="$DIAGNOSTICS_DIR/.export-$stamp-$$"
  state_archive="$DIAGNOSTICS_DIR/AshReXcue-diagnostics-$stamp.tar.gz"
  mkdir -p "$work/logs" "$work/rescue/history" "$work/rescue/restore_history" || return 1
  chmod 700 "$work" 2>/dev/null
  state_lock_acquire || { rm -rf "$work"; echo "Diagnostic state is busy."; return 3; }
  diagnostic_copy_text "$MODPATH/module.prop" "$work/module.prop"
  diagnostic_copy_text "$SETTINGS" "$work/settings.prop"
  diagnostic_copy_text "$PENDING_SETTINGS" "$work/pending-settings.prop"
  diagnostic_copy_text "$BOOT_STATE" "$work/boot_state.prop"
  diagnostic_copy_text "$BOOT_HISTORY" "$work/boot_times"
  diagnostic_copy_text "$BUILD_STATE" "$work/stable_build"
  diagnostic_copy_text "$SETTINGS_REPAIR_LOG" "$work/settings_repairs.log" 1000
  diagnostic_copy_text "$SETTINGS_CHANGE_LOG" "$work/settings_changes.tsv" 1000
  diagnostic_copy_text "$MODULE_LIST" "$work/module.json"
  list_modules_with_trust_tsv > "$work/module_inventory.tsv"
  dashboard_json > "$work/dashboard.json" 2>/dev/null || true
  pending_settings_json > "$work/pending_settings.json" 2>/dev/null || true
  rescue_history_json 50 0 all all "" > "$work/rescue/history.json" 2>/dev/null || true
  for file in "$RESCUE_HISTORY_DIR"/*.json; do [ -f "$file" ] && diagnostic_copy_text "$file" "$work/rescue/history/${file##*/}"; done
  for file in "$RESTORE_HISTORY_DIR"/*.prop; do [ -f "$file" ] && diagnostic_copy_text "$file" "$work/rescue/restore_history/${file##*/}"; done
  diagnostic_copy_text "$RESTORE_STATE" "$work/rescue/restore_state.prop"
  diagnostic_copy_text "$RESTORE_TRIAL" "$work/rescue/restore_trial.tsv"
  for file in "$LOG_DIR"/*.log; do [ -f "$file" ] && diagnostic_copy_text "$file" "$work/logs/${file##*/}" 4000; done
  {
    printf 'generated_at=%s\n' "$(date +%s 2>/dev/null || echo 0)"
    printf 'module_version=%s\n' "$(get_prop version "$MODPATH/module.prop")"
    printf 'module_version_code=%s\n' "$(get_prop versionCode "$MODPATH/module.prop")"
    printf 'root_manager=%s\n' "$method"
    printf 'root_version=%s\n' "$(get_root_version | tr '\r\n' ' ')"
    printf 'android_release=%s\n' "$(getprop ro.build.version.release 2>/dev/null)"
    printf 'android_sdk=%s\n' "$(getprop ro.build.version.sdk 2>/dev/null)"
    printf 'manufacturer=%s\n' "$(getprop ro.product.manufacturer 2>/dev/null)"
    printf 'model=%s\n' "$(getprop ro.product.model 2>/dev/null)"
    printf 'abi=%s\n' "$(getprop ro.product.cpu.abi 2>/dev/null)"
    printf 'boot_id=%s\n' "$(current_boot_id)"
  } | sanitize_diagnostic_stream > "$work/device.prop"
  cat > "$work/README.txt" <<'EOF'
AshReXcue sanitized diagnostic export

Included: validated settings, queued settings, durable boot state, module inventory,
rescue/restoration history, dashboard state, settings audit, and recent log tails.
Excluded/redacted: authentication data, Android IDs, serial numbers, IMEI/MEID,
and other matching authentication or device-identity lines.
EOF
  state_lock_release
  tar_bin=$(command -v tar 2>/dev/null)
  if [ -n "$tar_bin" ] && "$tar_bin" -czf "$state_archive" -C "$work" . 2>/dev/null; then :
  else
    bb=$(find_busybox 2>/dev/null)
    if [ -n "$bb" ] && "$bb" tar -czf "$state_archive" -C "$work" . 2>/dev/null; then :
    else
      state_archive="$DIAGNOSTICS_DIR/AshReXcue-diagnostics-$stamp.tar"
      if [ -n "$tar_bin" ]; then "$tar_bin" -cf "$state_archive" -C "$work" . || { rm -rf "$work"; return 1; }
      elif [ -n "$bb" ]; then "$bb" tar -cf "$state_archive" -C "$work" . || { rm -rf "$work"; return 1; }
      else rm -rf "$work"; echo "No tar implementation is available."; return 1
      fi
    fi
  fi
  chmod 600 "$state_archive" 2>/dev/null
  rm -rf "$work"
  output="$state_archive"
  if mkdir -p "$DIAGNOSTICS_DOWNLOAD_DIR" 2>/dev/null && [ -w "$DIAGNOSTICS_DOWNLOAD_DIR" ]; then
    final_archive="$DIAGNOSTICS_DOWNLOAD_DIR/${state_archive##*/}"
    if cp -f "$state_archive" "$final_archive" 2>/dev/null; then chmod 644 "$final_archive" 2>/dev/null; output="$final_archive"; fi
  fi
  # Retain only the five newest internal exports.
  copied=0
  for file in $(ls -1t "$DIAGNOSTICS_DIR"/AshReXcue-diagnostics-* 2>/dev/null); do
    [ -f "$file" ] || continue; copied=$((copied + 1)); [ "$copied" -le 5 ] || rm -f "$file"
  done
  echo "$output"
}
# --- AshReXcue 11.0: native companion application lifecycle ---
COMPANION_PACKAGE="com.mikeyphw.ashrexcue"
COMPANION_DIR="$ASHLOOPER_DIR/companion"
COMPANION_APK="$MODPATH/companion/AshReXcue.apk"
COMPANION_PERSISTENT_APK="$COMPANION_DIR/AshReXcue.apk"
COMPANION_VERSION_FILE="$MODPATH/companion/version_code"
COMPANION_PENDING="$COMPANION_DIR/install_pending"

companion_expected_version_code() {
  local value
  value=$(cat "$COMPANION_VERSION_FILE" 2>/dev/null)
  case "$value" in ''|*[!0-9]*) value=0 ;; esac
  echo "$value"
}

companion_user_id() {
  local value
  value=$(am get-current-user 2>/dev/null | tr -cd '0-9')
  case "$value" in ''|*[!0-9]*) value=0 ;; esac
  echo "$value"
}

companion_installed_version_code() {
  local value
  value=$(dumpsys package "$COMPANION_PACKAGE" 2>/dev/null | sed -n 's/^[[:space:]]*versionCode=\([0-9][0-9]*\).*/\1/p' | head -n 1)
  case "$value" in ''|*[!0-9]*) value=0 ;; esac
  echo "$value"
}

companion_installed_for_user() {
  local user_id
  user_id=$(companion_user_id)
  pm path --user "$user_id" "$COMPANION_PACKAGE" >/dev/null 2>&1
}

copy_companion_apk() {
  [ -f "$COMPANION_APK" ] || return 1
  mkdir -p "$COMPANION_DIR" || return 1
  chmod 700 "$COMPANION_DIR" 2>/dev/null
  atomic_copy_file "$COMPANION_APK" "$COMPANION_PERSISTENT_APK" 600 || cp -f "$COMPANION_APK" "$COMPANION_PERSISTENT_APK" || return 1
}

install_companion_app() {
  local expected installed source temp result rc=1 user_id
  initialize_storage
  copy_companion_apk || { log "Companion APK is not bundled."; return 1; }
  expected=$(companion_expected_version_code)
  installed=$(companion_installed_version_code)
  if companion_installed_for_user && [ "$installed" -gt 0 ] && [ "$expected" -gt 0 ] && [ "$installed" -ge "$expected" ] && [ ! -f "$COMPANION_PENDING" ]; then
    return 0
  fi
  command -v pm >/dev/null 2>&1 || { atomic_write_text "$COMPANION_PENDING" 600 "package manager unavailable\n"; return 1; }
  source="$COMPANION_PERSISTENT_APK"
  temp="/data/local/tmp/AshReXcue-$$.apk"
  cp -f "$source" "$temp" || { atomic_write_text "$COMPANION_PENDING" 600 "temporary copy failed\n"; return 1; }
  chmod 644 "$temp" 2>/dev/null
  chown 2000:2000 "$temp" 2>/dev/null || true
  user_id=$(companion_user_id)
  result=$(pm install -r --user "$user_id" "$temp" 2>&1); rc=$?
  if [ "$rc" -ne 0 ]; then result=$(pm install -r "$temp" 2>&1); rc=$?; fi
  rm -f "$temp"
  if [ "$rc" -eq 0 ]; then
    rm -f "$COMPANION_PENDING"
    log "Companion app installed/updated for Android user $user_id: $result"
    return 0
  fi
  atomic_write_text "$COMPANION_PENDING" 600 "$result\n"
  log "Companion app installation deferred: $result"
  return 1
}

launch_companion_app() {
  am start --user "$(companion_user_id)" -n "$COMPANION_PACKAGE/.MainActivity" >/dev/null 2>&1
}

#!/system/bin/sh

MODPATH="${0%/*}"
mkdir -p /data/adb/ashlooper/logs
. "$MODPATH/utils.sh" 2>>/data/adb/ashlooper/logs/looperbug.log || exit 1

ROOT_TYPE="$method"
apply_pending_settings || log "Queued settings could not be fully applied."
validate_settings
set_log_file
start_run
begin_boot_transaction
create_mod_list
handle_boot_loop "failed boot strike threshold"
modify_prop "+" "loops"
mark_boot_state booting "post-fs-data complete; awaiting boot signals"

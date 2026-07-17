#!/system/bin/sh
MODPATH="${0%/*}"
. "$MODPATH/utils.sh" >/dev/null 2>&1 || true
rm -rf /data/adb/ashlooper/companion 2>/dev/null

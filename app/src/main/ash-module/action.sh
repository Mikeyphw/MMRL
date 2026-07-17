#!/system/bin/sh
MODPATH="${0%/*}"
. "$MODPATH/utils.sh" || exit 1

echo ""
echo "=============================="
echo "       AshReXcue Actions"
echo "=============================="
echo "1. Restore next quarantined module"
echo "2. Restore half of quarantine"
echo "3. Restore all quarantined modules"
echo "4. Export diagnostics"
echo "5. Exit"
echo "=============================="
echo "Use volume keys in your root manager."
echo ""

choose_action() {
  local selected=1 key
  while true; do
    echo "-> $selected"
    chooseport; key=$?
    [ "$key" -eq 1 ] && { echo "$selected"; return; }
    [ "$key" -eq 0 ] && selected=$((selected % 5 + 1))
    if [ "$key" -eq 2 ]; then selected=$((selected - 1)); [ "$selected" -lt 1 ] && selected=5; fi
  done
}

selection=$(choose_action)
case "$selection" in
  1) restore_quarantined_modules next "" ;;
  2) restore_quarantined_modules half "" ;;
  3) restore_quarantined_modules all "" ;;
  4) create_diagnostic_export ;;
  *) exit 0 ;;
esac

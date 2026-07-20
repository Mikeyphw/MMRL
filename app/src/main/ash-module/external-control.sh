#!/system/bin/sh

# Stable, typed AshReXcue external-control protocol. No arbitrary command execution.
ASH_EXTERNAL_API_VERSION=1
ASH_EXTERNAL_SCHEMA='ashrexcue.external.v1'
ASH_EXTERNAL_DIR="$ASHLOOPER_DIR/external-control"
ASH_EXTERNAL_TOKEN_DIR="$ASH_EXTERNAL_DIR/tokens"
ASH_EXTERNAL_RECEIPT_DIR="$ASH_EXTERNAL_DIR/receipts"
ASH_EXTERNAL_OUTCOMES="$ASH_EXTERNAL_DIR/guidance-outcomes.tsv"
ASH_EXTERNAL_RATE_DIR="$ASH_EXTERNAL_DIR/rate"

ash_external_init() {
  mkdir -p "$ASH_EXTERNAL_TOKEN_DIR" "$ASH_EXTERNAL_RECEIPT_DIR" "$ASH_EXTERNAL_RATE_DIR" 2>/dev/null || return 1
  chmod 700 "$ASH_EXTERNAL_DIR" "$ASH_EXTERNAL_TOKEN_DIR" "$ASH_EXTERNAL_RECEIPT_DIR" "$ASH_EXTERNAL_RATE_DIR" 2>/dev/null
  [ -f "$ASH_EXTERNAL_OUTCOMES" ] || : > "$ASH_EXTERNAL_OUTCOMES"
  chmod 600 "$ASH_EXTERNAL_OUTCOMES" 2>/dev/null
}

ash_external_safe_key() {
  case "$1" in ''|*[!A-Za-z0-9._:-]*) return 1 ;; esac
  [ "${#1}" -ge 8 ] && [ "${#1}" -le 96 ] || return 1
  printf '%s' "$1" | tr ':' '_'
}

ash_external_valid_token() {
  case "$1" in ''|*[!A-Za-z0-9._:-]*) return 1 ;; esac
  [ "${#1}" -ge 16 ] && [ "${#1}" -le 160 ]
}

ash_external_now() { date +%s 2>/dev/null || echo 0; }

ash_external_envelope() {
  local ok="$1" action="$2" status="$3" message="$4" data_file="$5" replayed="${6:-false}"
  if [ -x "$JQ_BIN" ]; then
    if [ -n "$data_file" ] && [ -f "$data_file" ]; then
      "$JQ_BIN" -cn --argjson ok "$ok" --arg action "$action" --arg status "$status" --arg message "$(single_line "$message")" \
        --arg schema "$ASH_EXTERNAL_SCHEMA" --argjson replayed "$replayed" --slurpfile data "$data_file" \
        '{ok:$ok,apiVersion:1,schema:$schema,action:$action,status:$status,message:$message,replayed:$replayed,data:($data[0]//{})}'
    else
      "$JQ_BIN" -cn --argjson ok "$ok" --arg action "$action" --arg status "$status" --arg message "$(single_line "$message")" \
        --arg schema "$ASH_EXTERNAL_SCHEMA" --argjson replayed "$replayed" \
        '{ok:$ok,apiVersion:1,schema:$schema,action:$action,status:$status,message:$message,replayed:$replayed,data:{}}'
    fi
  else
    printf '{"ok":%s,"apiVersion":1,"schema":"%s","action":"%s","status":"%s","message":"%s","replayed":%s,"data":{}}\n' \
      "$ok" "$ASH_EXTERNAL_SCHEMA" "$(json_escape "$action")" "$(json_escape "$status")" "$(json_escape "$(single_line "$message")")" "$replayed"
  fi
}

ash_external_rate_limit() {
  local bucket="$1" limit="$2" window="$3" now file tmp count oldest
  now=$(ash_external_now); file="$ASH_EXTERNAL_RATE_DIR/$bucket"; tmp="$file.$$"
  awk -v now="$now" -v window="$window" '$1 ~ /^[0-9]+$/ && now-$1 >= 0 && now-$1 < window {print $1}' "$file" 2>/dev/null > "$tmp"
  count=$(wc -l < "$tmp" 2>/dev/null | tr -d ' '); [ -n "$count" ] || count=0
  if [ "$count" -ge "$limit" ]; then
    oldest=$(head -n 1 "$tmp" 2>/dev/null); rm -f "$tmp"
    ASH_EXTERNAL_RETRY_AFTER=$((oldest + window - now)); [ "$ASH_EXTERNAL_RETRY_AFTER" -gt 0 ] || ASH_EXTERNAL_RETRY_AFTER=1
    return 1
  fi
  printf '%s\n' "$now" >> "$tmp"
  mv -f "$tmp" "$file" || { rm -f "$tmp"; return 1; }
  chmod 600 "$file" 2>/dev/null
  return 0
}

ash_external_capabilities() {
  local data="$ASH_EXTERNAL_DIR/.capabilities.$$"
  ash_external_init || return 1
  if [ -x "$JQ_BIN" ]; then
    "$JQ_BIN" -cn '{protocolVersion:1,schema:"ashrexcue.external.v1",features:["capability-discovery","recovery-status","module-evidence","recovery-plan-preview","guarded-plan-execution","guidance-outcomes","idempotency","one-shot-tokens","rate-limits","dry-run","receipts"],exitCodes:{success:0,failure:1,invalidInput:2,busy:3,conflict:4,rateLimited:5,expired:6,executionFailed:7}}' > "$data"
  else
    printf '{}\n' > "$data"
  fi
  ash_external_envelope true capabilities OK 'AshReXcue external-control capabilities loaded.' "$data"
  rm -f "$data"
}

ash_external_status() {
  local data="$ASH_EXTERNAL_DIR/.status.$$"
  ash_external_init || return 1
  ash_external_rate_limit read 60 60 || { ash_external_envelope false status RATE_LIMITED "Retry in $ASH_EXTERNAL_RETRY_AFTER seconds." ""; return 5; }
  snapshot_json 100 > "$data" || { rm -f "$data"; ash_external_envelope false status FAILED 'Unable to load recovery status.' ""; return 1; }
  ash_external_envelope true status OK 'AshReXcue recovery status loaded.' "$data"
  rm -f "$data"
}

ash_external_evidence() {
  local filter="${1:-all}" raw="$ASH_EXTERNAL_DIR/.evidence-raw.$$" data="$ASH_EXTERNAL_DIR/.evidence.$$" jq_filter
  ash_external_init || return 1
  case "$filter" in all|quarantined|suspect|changed|needs-review) ;; *) ash_external_envelope false evidence INVALID_INPUT 'Invalid evidence filter.' ""; return 2 ;; esac
  ash_external_rate_limit read 60 60 || { ash_external_envelope false evidence RATE_LIMITED "Retry in $ASH_EXTERNAL_RETRY_AFTER seconds." ""; return 5; }
  modules_json > "$raw" || { rm -f "$raw"; ash_external_envelope false evidence FAILED 'Unable to load module evidence.' ""; return 1; }
  if [ -x "$JQ_BIN" ]; then
    case "$filter" in
      quarantined) jq_filter='.items|map(select(.quarantined==true))' ;;
      suspect) jq_filter='.items|map(select((.baseTrust//.trust)=="suspect"))' ;;
      changed) jq_filter='.items|map(select(.changedSinceStable==true))' ;;
      needs-review) jq_filter='.items|map(select(.quarantined==true or (.baseTrust//.trust)=="suspect" or .changedSinceStable==true))' ;;
      *) jq_filter='.items' ;;
    esac
    "$JQ_BIN" -c --arg filter "$filter" "{filter:\$filter,count:($jq_filter|length),items:$jq_filter}" "$raw" > "$data" || { rm -f "$raw" "$data"; return 1; }
  else
    cp "$raw" "$data"
  fi
  ash_external_envelope true evidence OK 'AshReXcue module evidence loaded.' "$data"
  rm -f "$raw" "$data"
}

ash_external_token_value() {
  local seed
  seed=$(cat /proc/sys/kernel/random/uuid 2>/dev/null | tr -d '-')
  [ -n "$seed" ] || seed=$(od -An -N16 -tx1 /dev/urandom 2>/dev/null | tr -d ' \n')
  [ -n "$seed" ] || seed=$(printf '%s' "$(ash_external_now).$$.$(current_boot_id)" | cksum | awk '{print $1"."$2}')
  printf 'ash1.%s.%s' "$seed" "$(printf '%s' "$seed.$$" | cksum | awk '{print $1}')"
}

ash_external_prepare() {
  local preset="${1:-conservative}" key="$2" requested="$3" dry_run="${4:-false}" safe token_file receipt_file now expires revision folders count trusted risk plan_id token binding data marker folder qtrust selected=''
  ash_external_init || return 1
  safe=$(ash_external_safe_key "$key") || { ash_external_envelope false prepare-plan INVALID_INPUT 'Invalid idempotency key.' ""; return 2; }
  case "$preset" in conservative|balanced|rapid|custom) ;; *) ash_external_envelope false prepare-plan INVALID_INPUT 'Invalid recovery preset.' ""; return 2 ;; esac
  case "$dry_run" in true|false) ;; *) ash_external_envelope false prepare-plan INVALID_INPUT 'Dry-run must be true or false.' ""; return 2 ;; esac
  receipt_file="$ASH_EXTERNAL_RECEIPT_DIR/$safe.json"
  if [ -f "$receipt_file" ]; then ash_external_envelope true prepare-plan REPLAYED 'Idempotent request already completed.' "$receipt_file" true; return 0; fi
  token_file="$ASH_EXTERNAL_TOKEN_DIR/$safe.json"
  if [ -f "$token_file" ]; then
    expires=$($JQ_BIN -r '.expiresAt//0' "$token_file" 2>/dev/null)
    now=$(ash_external_now)
    if [ "$expires" -gt "$now" ]; then ash_external_envelope true prepare-plan READY 'Existing recovery-plan token returned.' "$token_file" true; return 0; fi
    rm -f "$token_file"
  fi
  ash_external_rate_limit prepare 12 60 || { ash_external_envelope false prepare-plan RATE_LIMITED "Retry in $ASH_EXTERNAL_RETRY_AFTER seconds." ""; return 5; }
  case "$preset" in
    custom) selected="$requested" ;;
    *)
      for marker in "$QUARANTINE_DIR"/*.prop; do
        [ -f "$marker" ] || continue
        folder=$(get_prop folder "$marker"); qtrust=$(get_prop base_trust "$marker")
        [ -d "$mdir/$folder" ] && [ -f "$mdir/$folder/disable" ] || continue
        [ "$qtrust" = protected ] && continue
        selected="${selected}${selected:+,}$folder"
      done
      case "$preset" in
        conservative) selected=$(printf '%s' "$selected" | cut -d, -f1) ;;
        balanced)
          count=$(printf '%s' "$selected" | awk -F, '{print NF}')
          count=$(( (count + 1) / 2 )); [ "$count" -gt 4 ] && count=4
          selected=$(printf '%s' "$selected" | cut -d, -f1-"$count")
          ;;
      esac
      ;;
  esac
  [ -n "$selected" ] || { ash_external_envelope false prepare-plan BLOCKED 'No eligible quarantined modules were selected.' ""; return 4; }
  old_ifs="$IFS"; IFS=','; folders=''; count=0; trusted=0
  for folder in $selected; do
    case "$folder" in ''|*[!A-Za-z0-9._-]*) IFS="$old_ifs"; ash_external_envelope false prepare-plan INVALID_INPUT 'Invalid module folder.' ""; return 2 ;; esac
    marker="$QUARANTINE_DIR/$folder.prop"
    [ -f "$marker" ] && [ -d "$mdir/$folder" ] && [ -f "$mdir/$folder/disable" ] || { IFS="$old_ifs"; ash_external_envelope false prepare-plan BLOCKED "$folder is not live quarantine state." ""; return 4; }
    qtrust=$(get_prop base_trust "$marker")
    [ "$qtrust" != protected ] || { IFS="$old_ifs"; ash_external_envelope false prepare-plan BLOCKED "$folder is protected." ""; return 4; }
    [ "$qtrust" = trusted ] && trusted=$((trusted + 1))
    folders="${folders}${folders:+,}$folder"; count=$((count + 1))
    [ "$count" -le 8 ] || { IFS="$old_ifs"; ash_external_envelope false prepare-plan BLOCKED 'Recovery plan exceeds the eight-module safety limit.' ""; return 4; }
  done
  IFS="$old_ifs"
  revision=$(recovery_revision); [ -n "$revision" ] || { ash_external_envelope false prepare-plan BLOCKED 'Recovery revision is unavailable.' ""; return 4; }
  if [ "$count" -eq 1 ] && [ "$trusted" -eq 0 ]; then risk=Low; elif [ "$count" -le 4 ] && [ "$trusted" -eq 0 ]; then risk=Moderate; else risk=High; fi
  plan_id="plan-$preset-$(printf '%s' "$revision|$folders" | cksum | awk '{print $1}')"
  now=$(ash_external_now); expires=$((now + 1800)); token=''; [ "$dry_run" = true ] || token=$(ash_external_token_value)
  binding=$(printf '%s\n%s\n%s\n%s\n%s' "$plan_id" "$revision" "$folders" "$key" "$expires" | cksum | awk '{print $1"-"$2}')
  data="$ASH_EXTERNAL_DIR/.prepare.$$"
  "$JQ_BIN" -cn --arg token "$token" --arg key "$key" --arg planId "$plan_id" --arg preset "$preset" --arg revision "$revision" --arg folders "$folders" --arg risk "$risk" --arg binding "$binding" --argjson dryRun "$dry_run" --argjson createdAt "$now" --argjson expiresAt "$expires" \
    '{token:$token,idempotencyKey:$key,planId:$planId,preset:$preset,recoveryRevision:$revision,folders:($folders|split(",")),risk:$risk,dryRun:$dryRun,createdAt:$createdAt,expiresAt:$expiresAt,binding:$binding}' > "$data" || return 1
  if [ "$dry_run" = false ]; then cp "$data" "$token_file" && chmod 600 "$token_file" || { rm -f "$data"; return 1; }; fi
  ash_external_envelope true prepare-plan "$([ "$dry_run" = true ] && echo DRY_RUN || echo READY)" "$([ "$dry_run" = true ] && echo 'Recovery plan preview completed.' || echo 'Recovery plan is ready for guarded execution.')" "$data"
  rm -f "$data"
}

ash_external_execute() {
  local token="$1" key="$2" safe token_file receipt_file now expires revision current plan_id folders output rc data status
  ash_external_init || return 1
  safe=$(ash_external_safe_key "$key") || { ash_external_envelope false execute-plan INVALID_INPUT 'Invalid idempotency key.' ""; return 2; }
  ash_external_valid_token "$token" || { ash_external_envelope false execute-plan INVALID_INPUT 'Invalid automation token.' ""; return 2; }
  receipt_file="$ASH_EXTERNAL_RECEIPT_DIR/$safe.json"
  if [ -f "$receipt_file" ]; then ash_external_envelope true execute-plan REPLAYED 'Existing idempotent receipt returned.' "$receipt_file" true; return 0; fi
  ash_external_rate_limit execute 6 600 || { ash_external_envelope false execute-plan RATE_LIMITED "Retry in $ASH_EXTERNAL_RETRY_AFTER seconds." ""; return 5; }
  token_file="$ASH_EXTERNAL_TOKEN_DIR/$safe.json"; [ -f "$token_file" ] || { ash_external_envelope false execute-plan EXPIRED 'Automation token was not found or was already consumed.' ""; return 6; }
  [ "$($JQ_BIN -r '.token//""' "$token_file" 2>/dev/null)" = "$token" ] || { ash_external_envelope false execute-plan DENIED 'Automation token does not match this idempotency key.' ""; return 4; }
  now=$(ash_external_now); expires=$($JQ_BIN -r '.expiresAt//0' "$token_file"); [ "$expires" -gt "$now" ] || { rm -f "$token_file"; ash_external_envelope false execute-plan EXPIRED 'Automation token expired.' ""; return 6; }
  revision=$($JQ_BIN -r '.recoveryRevision//""' "$token_file"); current=$(recovery_revision)
  plan_id=$($JQ_BIN -r '.planId//""' "$token_file"); folders=$($JQ_BIN -r '.folders|join(",")' "$token_file")
  if [ "$revision" != "$current" ]; then
    data="$ASH_EXTERNAL_DIR/.execute.$$"
    "$JQ_BIN" -cn --arg idempotencyKey "$key" --arg planId "$plan_id" --arg revision "$revision" --arg folders "$folders" --arg currentRevision "$current" --argjson completedAt "$(ash_external_now)" \
      '{idempotencyKey:$idempotencyKey,planId:$planId,recoveryRevision:$revision,currentRevision:$currentRevision,folders:($folders|split(",")),status:"CONFLICT",message:"Recovery state changed after plan preparation.",completedAt:$completedAt}' > "$data"
    cp "$data" "$receipt_file" && chmod 600 "$receipt_file"
    rm -f "$token_file"
    ash_external_envelope false execute-plan CONFLICT 'Recovery state changed after plan preparation.' "$data"
    rm -f "$data"
    return 4
  fi
  output="$ASH_EXTERNAL_DIR/.execute-out.$$"; restore_quarantined_modules planned "$folders" "$revision" "$plan_id" > "$output" 2>&1; rc=$?
  data="$ASH_EXTERNAL_DIR/.execute.$$"; status=FAILED
  [ "$rc" -eq 0 ] && status=SUCCEEDED
  [ "$rc" -eq 3 ] && status=BUSY
  "$JQ_BIN" -cn --arg idempotencyKey "$key" --arg planId "$plan_id" --arg revision "$revision" --arg folders "$folders" --arg status "$status" --arg message "$(single_line "$(cat "$output" 2>/dev/null)")" --argjson completedAt "$(ash_external_now)" \
    '{idempotencyKey:$idempotencyKey,planId:$planId,recoveryRevision:$revision,folders:($folders|split(",")),status:$status,message:$message,completedAt:$completedAt}' > "$data"
  cp "$data" "$receipt_file" && chmod 600 "$receipt_file"; rm -f "$token_file" "$output"
  case "$rc" in
    0) ash_external_envelope true execute-plan SUCCEEDED 'Recovery plan started; reboot required.' "$data" ;;
    3) ash_external_envelope false execute-plan BUSY 'Recovery state is busy.' "$data" ;;
    *) ash_external_envelope false execute-plan FAILED 'Recovery plan execution failed.' "$data" ;;
  esac
  rm -f "$data"
  [ "$rc" -eq 0 ] && return 0
  [ "$rc" -eq 3 ] && return 3
  return 7
}

ash_external_outcome() {
  local recommendation="$1" folder="$2" outcome="$3" key="$4" safe receipt data now
  ash_external_init || return 1
  safe=$(ash_external_safe_key "$key") || { ash_external_envelope false guidance-outcome INVALID_INPUT 'Invalid idempotency key.' ""; return 2; }
  case "$recommendation" in ''|*[!A-Za-z0-9._:-]*) ash_external_envelope false guidance-outcome INVALID_INPUT 'Invalid recommendation ID.' ""; return 2 ;; esac
  case "$folder" in ''|*[!A-Za-z0-9._-]*) ash_external_envelope false guidance-outcome INVALID_INPUT 'Invalid module folder.' ""; return 2 ;; esac
  case "$outcome" in helped|failed|inconclusive) ;; *) ash_external_envelope false guidance-outcome INVALID_INPUT 'Outcome must be helped, failed, or inconclusive.' ""; return 2 ;; esac
  receipt="$ASH_EXTERNAL_RECEIPT_DIR/$safe.json"
  if [ -f "$receipt" ]; then ash_external_envelope true guidance-outcome REPLAYED 'Existing idempotent receipt returned.' "$receipt" true; return 0; fi
  ash_external_rate_limit outcome 20 60 || { ash_external_envelope false guidance-outcome RATE_LIMITED "Retry in $ASH_EXTERNAL_RETRY_AFTER seconds." ""; return 5; }
  now=$(ash_external_now); printf '%s\t%s\t%s\t%s\t%s\n' "$now" "$recommendation" "$folder" "$outcome" "$key" >> "$ASH_EXTERNAL_OUTCOMES"
  data="$ASH_EXTERNAL_DIR/.outcome.$$"
  "$JQ_BIN" -cn --arg idempotencyKey "$key" --arg recommendationId "$recommendation" --arg moduleFolder "$folder" --arg outcome "$outcome" --argjson completedAt "$now" '{idempotencyKey:$idempotencyKey,recommendationId:$recommendationId,moduleFolder:$moduleFolder,outcome:$outcome,status:"SUCCEEDED",completedAt:$completedAt}' > "$data"
  cp "$data" "$receipt" && chmod 600 "$receipt"
  ash_external_envelope true guidance-outcome SUCCEEDED 'Guidance outcome recorded.' "$data"
  rm -f "$data"
}

ash_external_receipt() {
  local safe receipt
  ash_external_init || return 1
  safe=$(ash_external_safe_key "$1") || { ash_external_envelope false receipt INVALID_INPUT 'Invalid idempotency key.' ""; return 2; }
  receipt="$ASH_EXTERNAL_RECEIPT_DIR/$safe.json"
  [ -f "$receipt" ] || { ash_external_envelope false receipt NOT_FOUND 'No completed receipt exists for this idempotency key.' ""; return 4; }
  ash_external_envelope true receipt OK 'Automation receipt loaded.' "$receipt"
}

ash_external_dispatch() {
  local action="$1"; shift
  case "$action" in
    capabilities) ash_external_capabilities ;;
    status) ash_external_status ;;
    evidence) ash_external_evidence "$@" ;;
    prepare) ash_external_prepare "$@" ;;
    execute) ash_external_execute "$@" ;;
    outcome) ash_external_outcome "$@" ;;
    receipt) ash_external_receipt "$@" ;;
    *) ash_external_envelope false automation INVALID_INPUT 'Unsupported automation action.' ""; return 2 ;;
  esac
}

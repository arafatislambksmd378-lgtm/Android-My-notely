#!/usr/bin/env bash
# Runs in CI AFTER ordinary instrumentation. Never points at a user's device.
set -euo pipefail
trap 'echo "::error title=Process recovery harness::Command failed at line $LINENO (stage ${stage:-startup})"' ERR
APP=com.notely.app.debug
RUNNER=com.notely.app.debug.test/androidx.test.runner.AndroidJUnitRunner
CLASS=com.notely.app.ProcessRecoveryInstrumentedTest
emulator=$(adb shell getprop ro.boot.qemu | tr -d '\r')
legacy=$(adb shell getprop ro.kernel.qemu | tr -d '\r')
[[ "$emulator" == 1 || "$legacy" == 1 ]]
mkdir -p process-recovery-results
stage_run() {
  local stage=$1 mode=$2
  adb shell run-as "$APP" rm -f files/recovery-e2e/ready
  adb shell am instrument -w -r -e class "$CLASS" -e recoveryStage "$stage" "$RUNNER" > "process-recovery-results/$stage.txt" 2>&1 &
  local harness=$!
  local ready=0
  for _ in $(seq 1 90); do
    marker=$(adb shell run-as "$APP" cat files/recovery-e2e/ready 2>/dev/null | tr -d '\r' || true)
    if [ "$marker" = "$stage" ]; then ready=1; break; fi
    if ! kill -0 "$harness" 2>/dev/null; then break; fi
    sleep 2
  done
  if [ "$ready" != 1 ]; then cat "process-recovery-results/$stage.txt"; echo "::error::Recovery stage $stage never acknowledged a durable snapshot"; exit 1; fi
  local before
  before=$(adb shell pidof "$APP" | tr -d '\r')
  test -n "$before"
  if [ "$mode" = force-stop ]; then
    adb shell am force-stop "$APP"
  else
    adb shell run-as "$APP" kill -9 "$before"
  fi
  wait "$harness" || true # termination is intentional, assertions above must already have passed
  test -z "$(adb shell pidof "$APP" | tr -d '\r')"
  echo "Stage $stage: durable acknowledgement then $mode, old PID $before terminated" | tee -a process-recovery-results/summary.txt
}
stage_run prepare force-stop
stage_run second SIGKILL
adb shell am instrument -w -r -e class "$CLASS" -e recoveryStage verify "$RUNNER" > process-recovery-results/verify.txt 2>&1
cat process-recovery-results/verify.txt
grep -q 'OK (1 test)' process-recovery-results/verify.txt
! grep -qE 'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=' process-recovery-results/verify.txt
adb shell run-as "$APP" cat files/recovery-e2e/verified | grep -q 'Force Stop and SIGKILL recovery verified'
echo '::notice title=Process recovery::PASS: acknowledged rich document survived Force Stop, then a second edit survived SIGKILL; UI reopened and verified. No lifecycle flush before either kill.'

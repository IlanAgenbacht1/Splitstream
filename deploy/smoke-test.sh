#!/usr/bin/env bash
# Runs a simulated race against the cluster and checks the leaderboard fills in, predicted finishes included.
# The prediction check matters: it's the finish model running inside the native race-service image.
#
#   deploy/smoke-test.sh            (needs kubectl, curl, jq, java and the built device-simulator jar)
set -euo pipefail

NS="${NAMESPACE:-splitstream}"
RUNNERS=6
TIMEOUT_SECONDS=180

kubectl -n "$NS" port-forward svc/ingest-service 8080:8080 > /dev/null 2>&1 &
kubectl -n "$NS" port-forward svc/race-service 8081:8081 > /dev/null 2>&1 &
trap 'kill $(jobs -p) 2> /dev/null || true' EXIT

wait_for() {
  for _ in $(seq 1 30); do
    if curl -sf "$1" > /dev/null; then return 0; fi
    sleep 2
  done
  echo "No answer from $1" >&2
  return 1
}
wait_for localhost:8080/actuator/health/readiness
wait_for localhost:8081/q/health/ready

# 10 km at 60x: long enough to pass 3 km, where predictions start, and short enough for CI
java -jar device-simulator/target/device-simulator.jar \
  --runners="$RUNNERS" --speedup=60 --distance=10000 --seed=7 > simulator.log 2>&1 &

for _ in $(seq 1 15); do
  RACE_ID=$(grep -o 'Race [0-9a-f-]*' simulator.log | head -1 | cut -d' ' -f2 || true)
  if [ -n "$RACE_ID" ]; then break; fi
  sleep 1
done
if [ -z "${RACE_ID:-}" ]; then
  echo "The simulator didn't create a race:" >&2
  cat simulator.log >&2
  exit 1
fi
echo "Race $RACE_ID"

saw_prediction=false
deadline=$((SECONDS + TIMEOUT_SECONDS))
while [ $SECONDS -lt $deadline ]; do
  board=$(curl -sf "localhost:8081/api/races/$RACE_ID/leaderboard" || echo '[]')
  runners=$(echo "$board" | jq length)
  predicted=$(echo "$board" | jq '[.[] | select(.predictedFinishSeconds != null)] | length')
  echo "leaderboard: $runners runners, $predicted with a predicted finish"
  if [ "$predicted" -gt 0 ]; then saw_prediction=true; fi

  if [ "$runners" -eq "$RUNNERS" ] && [ "$saw_prediction" = true ]; then
    echo "$board" | jq -c '.[0:3][]'
    echo "OK: every runner on the leaderboard, and predictions from the finish model"
    exit 0
  fi
  sleep 3
done

echo "Gave up after ${TIMEOUT_SECONDS}s. Last leaderboard:" >&2
echo "$board" >&2
tail -n 20 simulator.log >&2
exit 1

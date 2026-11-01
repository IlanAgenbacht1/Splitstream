#!/usr/bin/env bash
# Turns the [ERROR] lines of a Maven log into one annotation, so a failure is readable on the run's summary page
# without opening the logs.
#   .github/report-errors.sh maven.log
log="$1"
[ -f "$log" ] || exit 0

errors=$(grep -E '^\[ERROR\]' "$log" | grep -vE 'Re-run Maven|full stack trace|Help 1|^\[ERROR\] *$|For more information' | head -n 40)
[ -n "$errors" ] || errors=$(tail -n 40 "$log")

# Annotations are one line; %0A is how GitHub encodes a newline inside one
echo "::error title=Maven failed::$(echo "$errors" | sed ':a;N;$!ba;s/%/%25/g;s/\r//g;s/\n/%0A/g')"

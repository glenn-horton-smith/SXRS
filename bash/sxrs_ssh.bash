#!/bin/bash
#
# Helper script for sxrs on *nix systems
# mostly to keep xterm alive after ssh error,
# also gives user some helpful prompts
#
# usage:

USAGE="$0 SSH_PARAMETERS"

if [[ $1 == '--help' ]]; then
  echo Usage: $USAGE
  exit 0
fi

echo "ssh $1."

ssh $1

echo
echo ssh exit status $?
echo

read -p "Hit carriage return to exit."

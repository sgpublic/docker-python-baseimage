#!/usr/bin/env bash

set -e

. /setup-functions.sh

run_setup() {
  local SETUP_D=$1
  if [[ -d "$SETUP_D" ]]; then
    for i in $SETUP_D/*.sh; do
      if [ -r $i ]; then
        . $i
      fi
    done
    unset i
  fi
}

cp -a /etc/app-setup.d/*.sh /etc/app-setup.builtin.d
run_setup /etc/app-setup.builtin.d

if [[ -z "$APP_CACHE_HOME" ]]; then
  export APP_CACHE_HOME=$XDG_CACHE_HOME/$AUTO_VENV_NAME
fi

cd /app
gosu $PUID:$PGID bash -c /runner-entrypoint.sh

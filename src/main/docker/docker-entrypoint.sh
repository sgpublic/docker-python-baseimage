#!/usr/bin/env bash

set -e

. /setup-functions.sh

find /etc/app-setup.d -maxdepth 1 -type f -name "*.sh" -exec cp {} /etc/app-setup.builtin.d \;
for f in /etc/app-setup.d/*.sh; do [ -f "$f" ] && . "$f"; done

if [[ -z "$APP_CACHE_HOME" ]]; then
  export APP_CACHE_HOME=$XDG_CACHE_HOME/$AUTO_VENV_NAME
fi

cd /app
gosu $PUID:$PGID bash -c /runner-entrypoint.sh

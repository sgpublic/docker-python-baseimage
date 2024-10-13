#!/bin/bash

set -e

if [ -e "/setup" ]; then
  . /setup-functions.sh
	bash /setup
elif [ -e "./setup" ]; then
  . /setup-functions.sh
	bash ./setup
fi

if [[ -z "$APP_CACHE_HOME" ]]; then
  export APP_CACHE_HOME=$XDG_CACHE_HOME/$AUTO_VENV_NAME
fi

cd /app
gosu $PUID:$PGID bash -c /runner-entrypoint.sh

#!/bin/bash

if [ -e "/setup" ]; then
  . /setup-functions.sh
	bash /setup
elif [ -e "./setup" ]; then
  . /setup-functions.sh
	bash ./setup
fi

# adb
export PATH=$PATH:$ADB_HOME
# poetry
export PATH=$PATH:$POETRY_HOME/bin
# rust
export PATH=$PATH:$CARGO_HOME/bin

cd /app
export HOME="/home/poetry-runner"
ENTRYPOINT=./start
if [ -e "/start" ]; then
  ENTRYPOINT=/start
fi
su --preserve-environment poetry-runner bash -c $ENTRYPOINT

#!/bin/bash

if [ -e "/setup" ]; then
  . /setup-functions.sh
	bash /setup
elif [ -e "./setup" ]; then
  . /setup-functions.sh
	bash ./setup
fi

# poetry
export PATH=$PATH:$POETRY_HOME/bin

cd /app
export HOME="/home/poetry-runner"
ENTRYPOINT=./start
if [ -e "/start" ]; then
  ENTRYPOINT=/start
fi
su --preserve-environment poetry-runner bash -c $ENTRYPOINT

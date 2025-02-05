#!/usr/bin/env bash

set -e

POETRY_LOCK=/app/poetry.lock

mkdir -p $APP_CACHE_HOME

if [[ "$AUTO_VENV" == "1" ]]; then
  echo "启用自动管理 venv"
  if [[ -f "$POETRY_LOCK" ]]; then
    echo "$POETRY_LOCK 存在，跳过创建 venv"
  else
    if [[ ! -d "$APP_CACHE_HOME/venv" ]]; then
      echo "venv 不存在，自动创建：$APP_CACHE_HOME/venv"
      python3 -m venv $APP_CACHE_HOME/venv
    fi
    echo "激活 venv：$APP_CACHE_HOME/venv/bin/activate"
    source $APP_CACHE_HOME/venv/bin/activate
  fi
else
  echo "未启用自动管理 venv"
  if [[ ! -e "$POETRY_LOCK" ]]; then
    echo "且 $POETRY_LOCK 不存在，强制 AUTO_PIP_INSTALL=0"
    export AUTO_PIP_INSTALL=0
  fi
fi

if [[ "$AUTO_PIP_INSTALL" == "1" ]]; then
  echo "启用自动安装依赖"
  if [[ -f "$POETRY_LOCK" ]]; then
    echo "$POETRY_LOCK 存在"
    HASH_FILE=$APP_CACHE_HOME/poetry.lock.hash
    CURRENT_HASH=$(sha256sum "$POETRY_LOCK" | awk '{print $1}')

    if [ ! -f "$HASH_FILE" ] || [ "$CURRENT_HASH" != "$(cat "$HASH_FILE")" ]; then
      echo "$POETRY_LOCK 已变动，开执行安装..."
      poetry install
      echo "$CURRENT_HASH" > "$HASH_FILE"
    else
      echo "$POETRY_LOCK 未变动，跳过安装"
    fi
  elif [[ -f "$REQUIREMENTS_TXT" && "$AUTO_VENV" == 1 ]]; then
    echo "$REQUIREMENTS_TXT 存在"
    HASH_FILE=$APP_CACHE_HOME/requirements.txt.hash
    CURRENT_HASH=$(sha256sum "$REQUIREMENTS_TXT" | awk '{print $1}')

    if [ ! -f "$HASH_FILE" ] || [ "$CURRENT_HASH" != "$(cat "$HASH_FILE")" ]; then
      echo "$REQUIREMENTS_TXT 已变动，开执行安装..."
      pip install -r $REQUIREMENTS_TXT
      echo "$CURRENT_HASH" > "$HASH_FILE"
    else
      echo "$REQUIREMENTS_TXT 未变动，跳过安装"
    fi
  fi
else
  echo "未启用自动 pip install"
fi

ENTRYPOINT=./start
if [ -e "/start" ]; then
  ENTRYPOINT=/start
fi

echo "启动脚本：$ENTRYPOINT"
exec $ENTRYPOINT

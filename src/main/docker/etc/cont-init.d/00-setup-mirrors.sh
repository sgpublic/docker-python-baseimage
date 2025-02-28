#!/usr/bin/env bash

if [[ "$APT_MIRROR" != "" ]]; then
  local SOURCE_LIST=""
  if [[ -f "/etc/apt/sources.list" ]]; then
    SOURCE_LIST=/etc/apt/sources.list
  elif [[ -f "/etc/apt/sources.list.d/debian.sources" ]]; then
    SOURCE_LIST=/etc/apt/sources.list.d/debian.sources
  fi
  if [[ "$SOURCE_LIST" != "" ]]; then
    echo "设置 apt 源：$APT_MIRROR"
    sed -i "s/deb.debian.org/$APT_MIRROR/" $SOURCE_LIST
  fi
fi

if [[ "$PIP_MIRROR" != "" ]]; then
  echo "设置 pip 源：$APT_MIRROR"
  pip config set global.index-url $PIP_MIRROR
fi

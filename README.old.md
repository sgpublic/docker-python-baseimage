# poetry-docker

此镜像是为 NoneBot 应用构建的基础镜像，但适用于所有 Python 应用。

## 镜像特性

包含依赖：

+ git
+ sudo
+ curl
+ ffmpeg
+ libfreetype6-dev
+ poetry（安装目录：`/opt/poetry`，缓存目录：`/cache`）
+ playwright deps for chromium（仅标签中包含 playwright 时）

环境变量：

+ XDG_CACHE_HOME：缓存目录，默认 `/.cache`。
+ POETRY_CACHE_DIR：poetry 缓存目录，默认 `/.cache/poetry`。
+ AUTO_VENV：是否自动创建并使用 venv，推荐使用 requirements.txt 管理依赖的应用启用，默认 `0`。
+ AUTO_VENV_NAME：创建 venv 使用的文件夹名称，默认 `poetry-runner`。
+ APP_CACHE_HOME：创建 venv 所在路径，默认 `$XDG_CACHE_HOME/$AUTO_VENV_NAME`。
+ AUTO_PIP_INSTALL：是否自动安装依赖，默认 `0`，当 `AUTO_VENV` 为 0 且 `/app/poetry.lock` 不存在时强制置为 `0`。
+ REQUIREMENTS_TXT：pip install 所使用的 requirements.txt 文件路径，默认 `/app/requirements.txt`。
+ APT_MIRROR/PIP_MIRROR：设置 apt、pip 镜像源，留空则不设置，默认为空。

镜像启动流程：

+ 容器内工作目录为 `/app`
+ 以 `root:root` 身份执行初始化脚本 `setup`，寻找优先级：`/setup` -> `/app/setup`，若未寻找到则跳过。
+ 检查环境变量 `AUTO_VENV` 是否为 `1`，若为 `1` 且 `/app/poetry.lock` 不存在则自动创建 venv 到 `$APP_CACHE_HOME/venv` 并激活。
+ 检查环境变量 `AUTO_PIP_INSTALL` 是否为 `1`，若为 `1`：
  + 若 `/app/poetry.lock` 存在，则执行 `poetry install`。
  + 若 `$REQUIREMENTS_TXT` 存在且 `AUTO_VENV_NAME` 为 `1`，则执行 `pip intall -r $REQUIREMENTS_TXT`。
+ 以 `$PUID:$PGID` 身份执行启动脚本 `start`，寻找优先级：`/start` -> `/app/start`，若未寻找到则报错，无法启动镜像。

## 食用方法

选择一个你喜欢的目录，例如 `~/nonebot-app` 作为工作目录。

将 NoneBot 应用项目克隆至 `./app`，即 `~/nonebot-app/app`，创建一个启动脚本 `./start`，例如：

```shell
#!/bin/bash

poetry install
poetry run nb run
```

或不使用 poetry 的启动脚本：

```shell
#!/bin/bash

set -v

pip config set global.index-url https://mirrors.aliyun.com/pypi/simple/

pip install nonebot wheel
pip install -r requirements.txt

python3 run.py
```

然后创建 `./docker-compose.yaml`，例如：

```yaml
version: '3'
services:
  nonebot:
    image: mhmzx/poetry-runner:3.9-bullseye
    volumes:
      - /etc/localtime:/etc/localtime:ro
      - ./.cache:/home/poetry-runner/.cache
      - ./app:/app
      - ./start:/start
    restart: unless-stopped
    environment:
      TZ: Asia/Shanghai
      LANG: zh_CN.UTF8
```

## 从源代码构建

环境要求：

+ JDK 17

以 Python 3.10、CUDA 11.4 举例。

检查 [Python 版本合集](/versions/python.json) 中，目标 Python 版本支持哪些 Debian 系统版本，此处 Python 3.10 支持 `buster` 及以上，于是：

```shell
./gradlew buildPoetry310BusterImage # 构建仅 poetry 的 Debian 10 镜像
./gradlew buildPlaywright310BusterImage # 构建带 poetry 和 playwright 的 Debian 10 镜像
./gradlew buildPlaywright310BullseyeImage # 构建带 poetry 和 playwright 的 Debian 11 镜像
./gradlew buildPlaywright310BookwormImage # 构建带 poetry 和 playwright 的 Debian 12 镜像
# ...以此类推
```

检查 [CUDA 版本合集](/versions/cuda.json) 中，目标 CUDA 版本支持哪些 Debian 系统版本，此处 CUDA 11.4 仅支持 `buster`，于是：

```shell
./gradlew buildCuda114Poetry310BusterImage # 构建仅 poetry 的镜像
./gradlew buildCuda114Playwright310BusterImage # 构建带 poetry 和 playwright 的镜像
```

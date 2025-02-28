# docker-python-baseimage

这是一个 Python 基础镜像，基于 [jlesage/docker-baseimage](https://github.com/jlesage/docker-baseimage)/[jlesage/docker-baseimage-gui](https://github.com/jlesage/docker-baseimage-gui)，可选支持 GUI、Playwright、CUDA、CUDNN。其中 GUI、Playwright 已上传 Docker Hub，剩余可选需[从源码构建](#从源码构建)。

**注意：此仓库现与 release v49 及以前有破坏性更改，旧版本使用方法请查阅 [README.old.md](/README.old.md)！**

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

+ POETRY_CACHE_DIR：poetry 缓存目录，默认 `$XDG_CACHE_HOME/poetry`。
+ AUTO_VENV：是否自动创建并使用 venv，推荐使用 requirements.txt 管理依赖的应用启用，默认 `0`。
+ AUTO_VENV_NAME：创建 venv 使用的文件夹名称，默认 `poetry-runner`。
+ APP_CACHE_HOME：创建 venv 所在路径，默认 `$XDG_CACHE_HOME/$AUTO_VENV_NAME`。
+ AUTO_PIP_INSTALL：是否自动安装依赖，默认 `0`，当 `AUTO_VENV` 为 0 且 `/app/poetry.lock` 不存在时强制置为 `0`。
+ REQUIREMENTS_TXT：pip install 所使用的 requirements.txt 文件路径，默认 `/app/requirements.txt`。
+ APT_MIRROR/PIP_MIRROR：设置 apt、pip 镜像源，留空则不设置，默认为空。

## 从源码构建

环境要求：

+ JDK 17

以 Python 3.10、CUDA 11.4 举例。

检查 [Python 版本合集](/versions/python.json) 中，目标 Python 版本支持哪些 Debian 系统版本，此处 Python 3.10 支持 `buster` 及以上，于是：

```shell
./gradlew buildPoetry310BusterImage # 构建仅 poetry 的 Debian 10 镜像
./gradlew buildPlaywright310BusterImage # 构建带 poetry 和 playwright 的 Debian 10 镜像
./gradlew buildPlaywright310BullseyeImage # 构建带 poetry 和 playwright 的 Debian 11 镜像
./gradlew buildGuiPlaywright310BookwormImage # 构建支持 GUI、带 poetry 和 playwright 的 Debian 12 镜像
# ...以此类推
```

检查 [CUDA 版本合集](/versions/cuda.json) 中，目标 CUDA 版本支持哪些 Debian 系统版本，此处 CUDA 11.4 仅支持 `buster`，于是：

```shell
./gradlew buildCuda114Poetry310BusterImage # 构建仅 poetry 的镜像
./gradlew buildGuiCuda114Playwright310BusterImage # 构建支持 GUI、带 poetry 和 playwright 的镜像
```

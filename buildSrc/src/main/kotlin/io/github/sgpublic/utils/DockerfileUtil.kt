package io.github.sgpublic.utils

import java.time.ZoneId

fun commandLine(command: String): String {
    return Runtime.getRuntime().exec(command)
        .inputStream.reader().readText().trim()
}

fun command(vararg command: String?): String {
    return command.filterNotNull()
        .joinToString(" &&\\\n ")
}

fun aptInstall(vararg pkg: String): String {
    return "apt-get install -y ${pkg.joinToString(" ")}"
}

fun pipInstall(vararg pkg: String): String {
    return "pip install ${pkg.joinToString(" ")}"
}

fun rm(vararg file: String): String {
    return "rm -rf ${file.joinToString(" ")}"
}

fun replaceSourceListCommand(): String? = if (ZoneId.systemDefault().id == "Asia/Shanghai") {
    "sed -i 's/deb.debian.org/mirrors.aliyun.com/' \${__SOURCE_LIST_FILE}"
} else {
    null
}

fun replaceNvidiaSourceListCommand(): String? = if (ZoneId.systemDefault().id == "Asia/Shanghai") {
    "sed -i 's/developer.download.nvidia.com/developer.download.nvidia.cn/' /etc/apt/sources.list.d/cuda-debian\${DEBIAN_VERSION_INT}-$(arch).list"
} else {
    null
}

fun <T> choseByTimezone(asia: T, others: T): T {
    return if (ZoneId.systemDefault().id == "Asia/Shanghai") asia else others
}

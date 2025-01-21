package io.github.sgpublic.utils

import com.google.gson.GsonBuilder

internal val Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()
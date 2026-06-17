package com.isi.ump

/** Logcat 统一前缀，便于过滤 `TAG-->>UMP合规` */
object UmpLogTag {
    const val PREFIX = "TAG-->>"

    fun of(raw: String): String =
        if (raw.startsWith(PREFIX)) raw else PREFIX + raw
}

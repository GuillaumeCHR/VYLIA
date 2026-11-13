package com.vylia.privacy.domain.databases

/** Le texte téléchargé, ou `null` s'il n'a pas changé ; borné à [maxBytes] octets. */
suspend fun Fetch.text(url: String, maxBytes: Long, conditional: Boolean = true): String? =
    get(url, maxBytes, conditional)?.readText(Charsets.UTF_8)?.removePrefix("\uFEFF")

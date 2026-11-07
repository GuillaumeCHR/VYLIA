package com.vylia.privacy.data.web

import android.content.Context
import com.vylia.privacy.domain.web.WebServices

/** La liste des services web, embarquée (`assets/web_services.json`), lue une fois. Illisible : un bug de build. */
class WebServicesRepository(context: Context) {
    val services: WebServices by lazy {
        WebServices.parse(context.assets.open(ASSET).bufferedReader().use { it.readText() })
    }

    private companion object {
        const val ASSET = "web_services.json"
    }
}

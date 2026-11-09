package com.mekronize.app.net

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.Charset

/** Eski Android sürümlerinde bulunmayan Java 10 API'lerinin karşılıkları (bkz. buildSrc/CompatFactory). */
object Compat {
    @JvmStatic
    fun urlDecode(s: String, cs: Charset): String = URLDecoder.decode(s, cs.name())

    @JvmStatic
    fun urlEncode(s: String, cs: Charset): String = URLEncoder.encode(s, cs.name())
}

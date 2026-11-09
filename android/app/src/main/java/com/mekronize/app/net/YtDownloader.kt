package com.mekronize.app.net

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

/** NewPipe Extractor'ın ağ katmanı (OkHttp). */
object YtDownloader : Downloader() {
    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

    override fun execute(request: Request): Response {
        val body = request.dataToSend()?.toRequestBody(null, 0, request.dataToSend()!!.size)
        val builder = okhttp3.Request.Builder()
            .method(request.httpMethod(), body ?: if (request.httpMethod() == "POST") ByteArray(0).toRequestBody(null) else null)
            .url(request.url())
            .addHeader("User-Agent", USER_AGENT)

        for ((name, values) in request.headers()) {
            builder.removeHeader(name)
            for (v in values) builder.addHeader(name, v)
        }

        Http.client.newCall(builder.build()).execute().use { resp ->
            if (resp.code == 429) throw ReCaptchaException("reCaptcha", request.url())
            val text = resp.body?.string()
            val headers = HashMap<String, List<String>>()
            for (n in resp.headers.names()) headers[n] = resp.headers(n)
            return Response(resp.code, resp.message, headers, text, resp.request.url.toString())
        }
    }
}

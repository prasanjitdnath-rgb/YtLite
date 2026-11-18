package com.example.ytlite

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.concurrent.TimeUnit

class AppDownloader : Downloader() {
    private val client = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()

    override fun execute(request: Request): Response {
        val method = request.httpMethod()
        val url = request.url()
        val data = request.dataToSend()
        val body = when {
            data != null -> data.toRequestBody()
            method.equals("POST", true) -> ByteArray(0).toRequestBody()
            else -> null
        }
        val rb = okhttp3.Request.Builder().method(method, body).url(url)
            .addHeader("User-Agent", USER_AGENT)
        request.headers().forEach { (name, values) ->
            rb.removeHeader(name)
            values.forEach { rb.addHeader(name, it) }
        }
        client.newCall(rb.build()).execute().use { resp ->
            if (resp.code == 429) throw ReCaptchaException("reCaptcha challenge requested", url)
            return Response(
                resp.code, resp.message, resp.headers.toMultimap(),
                resp.body?.string(), resp.request.url.toString()
            )
        }
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"
    }
}

package com.example.animewatch.data

import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.internal.http2.ErrorCode
import okhttp3.internal.http2.StreamResetException
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

class AppErrorsTest {

    private fun http(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody()))

    @Test
    fun `rete assente o caduta`() {
        assertEquals(ErrorKind.OFFLINE, AppErrors.kind(UnknownHostException("graphql.anilist.co")))
        assertEquals(ErrorKind.OFFLINE, AppErrors.kind(ConnectException("failed to connect")))
        assertEquals(ErrorKind.OFFLINE, AppErrors.kind(SocketTimeoutException("timeout")))
        assertEquals(ErrorKind.OFFLINE, AppErrors.kind(SSLHandshakeException("handshake")))
        assertEquals(ErrorKind.OFFLINE, AppErrors.kind(StreamResetException(ErrorCode.CANCEL)))
    }

    @Test
    fun `errore di rete avvolto in un altro`() {
        assertEquals(ErrorKind.OFFLINE, AppErrors.kind(RuntimeException("wrap", UnknownHostException())))
    }

    @Test
    fun `servizio che risponde con errore`() {
        assertEquals(ErrorKind.SERVICE, AppErrors.kind(http(429)))
        assertEquals(ErrorKind.SERVICE, AppErrors.kind(http(503)))
        assertEquals(ErrorKind.SERVICE, AppErrors.kind(ServiceException("AniList: Too Many Requests.")))
    }

    @Test
    fun `download corrotto e spazio esaurito`() {
        assertEquals(ErrorKind.CORRUPT_DOWNLOAD, AppErrors.kind(CorruptDownloadException("sha256 diversa")))
        assertEquals(ErrorKind.NO_SPACE, AppErrors.kind(IOException("write failed: ENOSPC (No space left on device)")))
    }

    @Test
    fun `il resto è inatteso`() {
        assertEquals(ErrorKind.UNKNOWN, AppErrors.kind(IllegalStateException("boom")))
        assertEquals(ErrorKind.UNKNOWN, AppErrors.kind(IOException("impossibile salvare l'aggiornamento")))
    }

    @Test
    fun `una causa circolare non blocca`() {
        val a = IOException("a")
        val b = IllegalStateException("b", a)
        a.initCause(b)
        assertEquals(ErrorKind.UNKNOWN, AppErrors.kind(a))
    }
}

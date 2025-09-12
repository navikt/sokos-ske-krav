package no.nav.sokos.ske.krav.util

import java.io.File
import java.io.Reader

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk

import no.nav.sokos.ske.krav.dto.ske.responses.FeilResponse

object TestUtilFunctions {
    fun fileAsString(fileName: String): String = fileAs(fileName, Reader::readText)

    fun getFileContent(filename: String) = fileAs("${File.separator}FtpFiler${File.separator}/$filename", Reader::readLines)

    fun mockHttpResponse(
        code: Int,
        feilResponseType: String = "",
    ) = mockk<HttpResponse> {
        every { status.value } returns code
        coEvery { body<FeilResponse>().type } returns feilResponseType
    }

    private fun <T> fileAs(
        fileName: String,
        func: Reader.() -> T,
    ): T =
        this::class.java
            .getResourceAsStream(fileName)!!
            .bufferedReader()
            .use { it.func() }
}

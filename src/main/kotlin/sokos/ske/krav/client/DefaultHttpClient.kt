package sokos.ske.krav.client

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.jackson.*
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import java.net.ProxySelector


val defaultHttpClient = HttpClient(Apache) {
    install(ContentNegotiation) {
        jackson {
            findAndRegisterModules()
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            enable(SerializationFeature.INDENT_OUTPUT)
            setSerializationInclusion(JsonInclude.Include.NON_NULL)

        }
    }
    install(HttpRequestRetry) {
        retryOnException(maxRetries = 3)
        delayMillis { retry -> retry * 3000L }
    }

    engine { customizeClient { setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault())) } }
}

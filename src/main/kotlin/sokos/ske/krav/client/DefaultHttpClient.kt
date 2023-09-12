package sokos.ske.krav.client

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.jackson.*
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import java.net.ProxySelector

fun ObjectMapper.customConfig() {
    registerModule(JavaTimeModule())
    configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
}

val defaultHttpClient = HttpClient(Apache) {
    install(ContentNegotiation) {
        jackson {
            customConfig()
        }
    }
    install(HttpRequestRetry) {
        //  retryOnServerErrors(maxRetries = 3)
        retryOnException(maxRetries = 3)
        delayMillis { retry -> retry * 3000L }
    }

    engine { customizeClient { setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault())) } }
}

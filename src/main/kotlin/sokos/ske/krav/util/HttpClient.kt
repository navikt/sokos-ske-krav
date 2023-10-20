package sokos.ske.krav.util

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.plugins.HttpRequestRetry
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import java.net.ProxySelector


val httpClient = HttpClient(Apache) {
    expectSuccess = false

    install(HttpRequestRetry) {
        retryOnException(maxRetries = 3)
        delayMillis { retry -> retry * 3000L }
    }

    engine {
        customizeClient {
            setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
        }
    }
}

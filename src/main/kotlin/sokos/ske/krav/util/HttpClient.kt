package sokos.ske.krav.util

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson.jackson
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import java.net.ProxySelector

val httpClient = HttpClient(Apache) {
	expectSuccess = false

	install(HttpRequestRetry) {
		retryOnException(maxRetries = 3)
		delayMillis { retry -> retry * 3000L }
	}

	install(ContentNegotiation) {
		jackson {
			findAndRegisterModules()
			disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
			disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			enable(SerializationFeature.INDENT_OUTPUT)
			setSerializationInclusion(JsonInclude.Include.NON_NULL)

		}
	}

	engine {
		customizeClient {
			setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
		}
	}
}

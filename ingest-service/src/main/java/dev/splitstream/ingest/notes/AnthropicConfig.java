package dev.splitstream.ingest.notes;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
class AnthropicConfig {

    /** Built on first use, so the rest of the service starts and runs without an API key. */
    @Bean(destroyMethod = "close")
    @Lazy
    AnthropicClient anthropicClient() {
        return AnthropicOkHttpClient.fromEnv();
    }
}

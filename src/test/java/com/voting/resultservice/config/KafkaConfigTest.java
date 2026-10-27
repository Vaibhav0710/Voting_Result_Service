package com.voting.resultservice.config;

import com.voting.resultservice.event.VoteCastEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.Consumer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaConfigTest {

    @Test
    void voteCastConsumerReportsKafkaClientMetrics() {
        MeterRegistry registry = new SimpleMeterRegistry();
        KafkaConfig config = new KafkaConfig("localhost:9092", "result-service-group", registry);

        // Creating a consumer does not connect to the broker, so no Kafka is needed
        try (Consumer<String, VoteCastEvent> consumer = config.voteCastConsumerFactory().createConsumer()) {
            assertThat(registry.getMeters())
                    .anyMatch(meter -> meter.getId().getName().startsWith("kafka.consumer"));
        }
    }
}

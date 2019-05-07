package io.pivotal.spinnaker.multifoundationmetrics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.frigga.Names;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.simple.CountingMode;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.micrometer.prometheus.rsocket.PrometheusRSocketClient;
import io.prometheus.client.CollectorRegistry;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.repository.Repository;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SpringBootApplication
public class ExampleApplication {
    public static void main(String[] args) {
        SpringApplication.run(ExampleApplication.class, args);
    }
}

@Configuration
class MetricsConfiguration {
    @Bean
    PrometheusMeterRegistry prometheusMeterRegistry(CollectorRegistry collectorRegistry) {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT, collectorRegistry, Clock.SYSTEM);

        PrometheusRSocketClient.connect(registry, "23.251.146.199", 7001)
          .retryBackoff(Long.MAX_VALUE, Duration.ofSeconds(10), Duration.ofMinutes(10))
          .subscribe();

        return registry;
    }

    @Bean
    MeterFilter commonTags(@Value("${cf.foundation:local}") String foundation,
                           @Value("${VCAP_APPLICATION:#{null}}") String app,
                           @Value("${CF_INSTANCE_INDEX:0}") String instanceIndex) {
        String serverGroup = Optional.ofNullable(app).map(app2 -> {
            try {
                Map<String, Object> vcapApplication = new ObjectMapper().readValue(app, new TypeReference<Map<String, Object>>() {
                });
                return (String) vcapApplication.getOrDefault("application_name", "unknown");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }).orElse("unknown");

        Names names = Names.parseName(serverGroup);

        return MeterFilter.commonTags(Tags.of("foundation", foundation, "app", names.getApp(), "cluster", names.getCluster(),
          "cf.instance.number", instanceIndex));
    }

    @Bean
    public SimpleMeterRegistry registry() {
        SimpleConfig config = new SimpleConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public CountingMode mode() {
                return CountingMode.STEP;
            }
        };

        return new SimpleMeterRegistry(config, Clock.SYSTEM);
    }
}

@Data
@Document
class Person {
    @NonNull
    @Id
    private String id;

    @NonNull
    private String firstName;

    @NonNull
    private String lastName;
}

interface PersonRepository extends Repository<Person, String> {
    Flux<Person> findAll();
}

@RestController
class PersonController {
    private final PersonRepository repository;
    private volatile boolean stable = true;

    public PersonController(PersonRepository repository) {
        this.repository = repository;
    }

    @GetMapping(path = "/persons")
    public Flux<Person> all() {
        if (!stable) {
            throw new RuntimeException("i'm unstable");
        }
        return this.repository.findAll();
    }

    @GetMapping(path = "/destabilize")
    public String destabilize() {
        this.stable = false;
        return "i'm going to start breaking stuff";
    }

    @GetMapping(path = "/stabilize")
    public String stabilize() {
        this.stable = true;
        return "i'm healthy again";
    }

}

@Configuration
class EndpointHealthConfiguration {
    @Value("${endpointFailureRateThreshold:0.01}")
    private Double endpointFailureRateThreshold;

    @Bean
    public HealthIndicator endpointHealthIndicator(SimpleMeterRegistry registry) {
        return () -> Stream.of(
                registry.find("http.server.requests")
                        .tag("uri", "/persons").timers()
        ).map(requestTimers -> {
            Map<Boolean, Long> requestsBySuccess = requestTimers.stream().collect(
                    Collectors.partitioningBy(t -> "200".equals(t.getId().getTag("status")),
                            Collectors.summingLong(Timer::count)));
            Long successes = requestsBySuccess.getOrDefault(true, 0L);
            Long total = successes + requestsBySuccess.getOrDefault(false, 0L);
            double failureRate = (double) (total - successes) / total;
            return (failureRate > endpointFailureRateThreshold ? Health.down() : Health.up())
                    .withDetail("endpoint.failure.rate", failureRate)
                    .withDetail("endpoint.successes", successes)
                    .withDetail("endpoint.total", total)
                    .build();
        }).findFirst().orElse(Health.up().build());
    }
}

package io.pivotal.spinnaker.multifoundationmetrics;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;

@Configuration
public class MetricsConfiguration {
  private final Logger logger = LoggerFactory.getLogger(MetricsConfiguration.class);

  @Value("${spring.application.name:unknown}")
  private String appName;

  @Value("${HOSTNAME:unknown}")
  private String host;

  @Bean
  @ConditionalOnBean(KubernetesClient.class)
  MeterFilter kubernetesMeterFilter(KubernetesClient k8sClient) {
    return new MeterFilter() {
      private Function<Meter.Id, Meter.Id> idMapper;

      {
        try {
          Map<String, String> annotations = k8sClient.pods()
            .withName(host)
            .get()
            .getMetadata()
            .getAnnotations();

          for (Map.Entry<String, String> annotation : annotations.entrySet()) {
            logger.info("Kubernetes pod annotation <" + annotation.getKey() + "=" + annotation.getValue() + ">");
          }

          idMapper = id -> id.withTags(Tags.of(
            "revision", annotations.getOrDefault("deployment.kubernetes.io/revision", "unknown"),
            "app", annotations.getOrDefault("moniker.spinnaker.io/application", appName),
            "cluster", Arrays.stream(annotations.getOrDefault("moniker.spinnaker.io/cluster", "unknown")
              .split(" ")).reduce((first, second) -> second).orElse("unknown"),
            "location", annotations.getOrDefault("artifact.spinnaker.io/location", "unknown"),
            "host", host
          ));
        } catch(KubernetesClientException e) {
          logger.warn("Unable to apply kubernetes tags", e);
          idMapper = id -> id.withTags(Tags.of("app", appName, "host", host, "cluster", "unknown"));
        }
      }

      @Override
      public Meter.Id map(Meter.Id id) {
        return idMapper.apply(id);
      }
    };
  }

  @Bean
  @ConditionalOnMissingBean(KubernetesClient.class)
  MeterFilter appAndHostTagsMeterFilter() {
    return MeterFilter.commonTags(Tags.of("app", appName, "host", host));
  }

  @Bean
  MeterFilter httpDistributionStats() {
    return new MeterFilter() {
      @Override
      public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
        if(id.getName().equals("http.server.requests")) {
          return DistributionStatisticConfig.builder().percentilesHistogram(true).build().merge(config);
        }
        return config;
      }
    };
  }
}

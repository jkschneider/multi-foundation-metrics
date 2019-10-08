package io.pivotal.spinnaker.multifoundationmetrics;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static java.util.Collections.emptyMap;

@Configuration
@ConditionalOnBean(KubernetesClient.class)
public class PaneraMetricsConfiguration {
  @Bean
  MeterFilter kubernetesMeterFilter(KubernetesClient k8sClient,
                                    @Value("${spring.application.name:unknown}") String appName) {
    Map<String, String> annotations = k8sClient.pods()
      .withName(System.getenv("HOSTNAME"))
      .get()
      .getMetadata()
      .getOwnerReferences()
      .stream()
      .findFirst()
      .map(ref -> k8sClient.apps().replicaSets().withName(ref.getName()).get())
      .map(replicaSet -> replicaSet.getMetadata().getAnnotations())
      .orElse(emptyMap());

    return new MeterFilter() {
      @Override
      public Meter.Id map(Meter.Id id) {
        return id.withTags(Tags.of(
          "revision", annotations.getOrDefault("deployment.kubernetes.io/revision", "unknown"),
          "application", annotations.getOrDefault("moniker.spinnaker.io/application", appName),
          "cluster", annotations.getOrDefault("moniker.spinnaker.io/cluster", "unknown"),
          "location", annotations.getOrDefault("artifact.spinnaker.io/location", "unknown")
        ));
      }

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

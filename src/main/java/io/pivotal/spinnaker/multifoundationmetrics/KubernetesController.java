package io.pivotal.spinnaker.multifoundationmetrics;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.kubernetes.PodUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.emptyMap;

@RestController
public class KubernetesController {
  private final PodUtils podUtils;

  public KubernetesController(PodUtils podUtils, KubernetesClient client) {
    this.podUtils = podUtils;

    Logger logger = LoggerFactory.getLogger("k8s");
    Pod pod = podUtils.currentPod().get();

    Optional<ReplicaSet> replicaSet = pod.getMetadata().getOwnerReferences().stream()
      .findFirst()
      .map(ref -> client.apps().replicaSets().withName(ref.getName()).get());

    Optional<Deployment> deployment = replicaSet
      .flatMap(rs -> rs.getMetadata().getOwnerReferences().stream().findFirst())
      .map(ref -> client.apps().deployments().withName(ref.getName()).get());

    Map<String, String> annotations = Stream
      .of(
        pod.getMetadata().getAnnotations(),
        replicaSet.map(rs -> rs.getMetadata().getAnnotations()).orElse(emptyMap()),
        deployment.map(d -> d.getMetadata().getAnnotations()).orElse(emptyMap()),
      )
      .flatMap(a -> a.entrySet().stream())
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    for (Map.Entry<String, String> annot : annotations.entrySet()) {
      logger.warn(annot.getKey() + "=" + annot.getValue());
    }
  }

  @GetMapping("/k8s/annotations")
  public Map<String, String> annotations() {
    return podUtils.currentPod().get().getMetadata().getAnnotations();
  }
}

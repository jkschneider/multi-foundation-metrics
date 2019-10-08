package io.pivotal.spinnaker.multifoundationmetrics;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.kubernetes.PodUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class KubernetesController {
  private final PodUtils podUtils;

  public KubernetesController(PodUtils podUtils, KubernetesClient client) {
    this.podUtils = podUtils;

    Logger logger = LoggerFactory.getLogger("k8s");
    Pod pod = podUtils.currentPod().get();
    logger.warn(pod.getSpec().toString());
    logger.warn(pod.getMetadata().toString());

    for (Map.Entry<String, String> annot : pod.getMetadata().getAnnotations().entrySet()) {
      logger.warn(annot.getKey() + "=" + annot.getValue());
    }
  }

  @GetMapping("/k8s/annotations")
  public Map<String, String> annotations() {
    return podUtils.currentPod().get().getMetadata().getAnnotations();
  }
}

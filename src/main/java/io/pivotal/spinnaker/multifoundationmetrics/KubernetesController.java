package io.pivotal.spinnaker.multifoundationmetrics;

import org.slf4j.LoggerFactory;
import org.springframework.cloud.kubernetes.PodUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class KubernetesController {
  private final PodUtils podUtils;

  public KubernetesController(PodUtils podUtils) {
    this.podUtils = podUtils;
    for (Map.Entry<String, String> annot : podUtils.currentPod().get().getMetadata().getAnnotations().entrySet()) {
      LoggerFactory.getLogger("k8s").warn(annot.getKey() + "=" + annot.getValue());
    }
  }

  @GetMapping("/k8s/annotations")
  public Map<String, String> annotations() {
    return podUtils.currentPod().get().getMetadata().getAnnotations();
  }
}

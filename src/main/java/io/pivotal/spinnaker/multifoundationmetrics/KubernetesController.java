package io.pivotal.spinnaker.multifoundationmetrics;

import org.springframework.cloud.kubernetes.PodUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class KubernetesController {
  private final PodUtils podUtils;

  public KubernetesController(PodUtils podUtils) {
    this.podUtils = podUtils;
  }

  @GetMapping("/k8s/annotations")
  public Map<String, String> annotations() {
    return podUtils.currentPod().get().getMetadata().getAnnotations();
  }
}

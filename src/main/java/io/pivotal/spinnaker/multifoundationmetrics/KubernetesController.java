package io.pivotal.spinnaker.multifoundationmetrics;

import org.springframework.cloud.kubernetes.PodUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class KubernetesController {
  @GetMapping("/k8s/annotations")
  public Map<String, String> annotations(PodUtils podUtils) {
    return podUtils.currentPod().get().getMetadata().getAnnotations();
  }
}

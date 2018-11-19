package io.pivotal.spinnaker.multifoundationmetrics;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.repository.Repository;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

@SpringBootApplication
public class ExampleApplication {
  public static void main(String[] args) {
    SpringApplication.run(ExampleApplication.class, args);
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

  public PersonController(PersonRepository repository) {
    this.repository = repository;
  }

  @GetMapping(path = "/persons")
  public Flux<Person> all(@RequestParam(value = "failme", defaultValue = "false") Boolean failme) {
    if (failme) {
      throw new RuntimeException("i was told to fail");
    }
    return this.repository.findAll();
  }
}

@RestController
class DatadogVerificationController {
  private final String apiKey;
  private final String appKey;

  DatadogVerificationController(@Value("${management.metrics.export.datadog.api-key}") String apiKey,
                                @Value("${management.metrics.export.datadog.application-key}") String appKey) {
    this.apiKey = apiKey;
    this.appKey = appKey;
  }

  @GetMapping(path = "/datadog/keys")
  public Map<String, String> keys() {
    return new HashMap<String, String>() {{
      put("api-key", apiKey);
      put("app-key", appKey);
    }};
  }
}
package io.pivotal.spinnaker.multifoundationmetrics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.frigga.Names;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.MeterFilter;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.repository.Repository;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Optional;

@SpringBootApplication
public class ExampleApplication {
  public static void main(String[] args) {
    SpringApplication.run(ExampleApplication.class, args);
  }

  @Bean
  MeterFilter commonTags(@Value("${cf.foundation:local}") String foundation,
                         @Value("${cf.org:local}") String org,
                         @Value("${cf.space:local}") String space,
                         @Value("${CF_INSTANCE_GUID:local}") String instance,
                         @Value("${VCAP_APPLICATION:#{null}}") String app) throws IOException {
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

    return MeterFilter.commonTags(Tags.of("foundation", foundation, "org", org, "space", space, "instance", instance,
      "app", names.getApp(), "cluster", names.getCluster(), "version", names.getSequence() == null ? "v000" : names.getSequence().toString()));
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


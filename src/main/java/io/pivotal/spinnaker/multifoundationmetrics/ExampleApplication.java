package io.pivotal.spinnaker.multifoundationmetrics;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SpringBootApplication
public class ExampleApplication {
  public static void main(String[] args) {
    SpringApplication.run(ExampleApplication.class, args);
  }

  private Random r = new Random();

  @Bean
  PersonRepository personRepository() {
    List<String> lastNames = names("/dist.all.last");
    List<String> femaleFirst = names("/dist.female.first");
    List<String> maleFirst = names("/dist.male.first");

    return () -> Stream.concat(maleFirst.stream(), femaleFirst.stream())
      .flatMap(first -> lastNames.stream().map(last -> new Person(first, last)))
      .collect(Collectors.toList());
  }

  private List<String> names(String fileName) {
    try (BufferedReader buffer = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream(fileName)))) {
      return buffer.lines()
        .limit(Math.min(5, r.nextInt(20)))
        .map(name -> name.split(" ")[0])
        .collect(Collectors.toList());
    } catch (IOException e) {
      e.printStackTrace();
    }
    return Collections.emptyList();
  }
}

@Getter
@AllArgsConstructor
class Person {
  private String firstName;
  private String lastName;
}

interface PersonRepository {
  List<Person> findAll();
}

@RestController
class PersonController {
  private final PersonRepository repository;
  private volatile boolean stable = true;

  public PersonController(PersonRepository repository) {
    this.repository = repository;
  }

  @GetMapping(path = "/persons")
  public List<Person> all() {
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

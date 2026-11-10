package com.auxiruta.assistance.requests;

import java.util.Map;
import java.io.InputStream;
import org.springframework.stereotype.Component;
import org.springframework.core.io.ClassPathResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.yaml.snakeyaml.Yaml;

@Component
public class RulesConfiguration {
  private final String snapshot;
  private final Map<String, Object> rules;

  public RulesConfiguration(ObjectMapper mapper) throws Exception {
    this(mapper, new org.springframework.core.env.StandardEnvironment());
  }

  @org.springframework.beans.factory.annotation.Autowired
  public RulesConfiguration(
      ObjectMapper mapper, org.springframework.core.env.Environment environment) throws Exception {
    try (InputStream in = new ClassPathResource("rules/mvp.yml").getInputStream()) {
      Map<String, Object> document = new Yaml().load(in);
      rules =
          mapper.convertValue(
              document.get("auxiruta"),
              new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
      if (java.util.Arrays.asList(environment.getActiveProfiles()).contains("demo")) {
        rules.put("rules-version", "AR-MVP-5-DEMO");
        override("search", "attempt-timeout-seconds", 60);
        override("search", "evaluation-interval-seconds", 2);
        override("search", "minimum-offer-window-seconds", 3);
        override("fleet", "hold-seconds", 10);
        override("execution", "dispatch-deadline-seconds", 20);
      }
      snapshot = mapper.writeValueAsString(rules);
    }
  }

  @SuppressWarnings("unchecked")
  private void override(String group, String name, int value) {
    ((Map<String, Object>) rules.get(group)).put(name, value);
  }

  public String snapshot() {
    return snapshot;
  }

  public String revision() {
    return rules.get("rules-version").toString();
  }

  public int searchSeconds() {
    return ((Number) ((Map<?, ?>) rules.get("search")).get("attempt-timeout-seconds")).intValue();
  }

  public Map<String, Object> effective() {
    return rules;
  }
}

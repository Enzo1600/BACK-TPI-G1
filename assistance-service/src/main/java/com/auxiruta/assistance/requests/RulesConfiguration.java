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
 private final Map<String,Object> rules;
 public RulesConfiguration(ObjectMapper mapper) throws Exception {
  try(InputStream in=new ClassPathResource("rules/mvp.yml").getInputStream()) {
   Map<String,Object> document=new Yaml().load(in);
   rules=mapper.convertValue(document.get("auxiruta"),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>() {});
   snapshot=mapper.writeValueAsString(rules);
  }
 }
 public String snapshot() { return snapshot; }
 public String revision() { return rules.get("rules-version").toString(); }
 public int searchSeconds() { return ((Number)((Map<?,?>)rules.get("search")).get("attempt-timeout-seconds")).intValue(); }
 public Map<String,Object> effective() { return rules; }
}

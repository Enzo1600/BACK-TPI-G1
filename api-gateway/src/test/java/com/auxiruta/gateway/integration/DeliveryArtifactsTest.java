package com.auxiruta.gateway.integration;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.auxiruta.gateway.integration.BackendClient.JSON;

class DeliveryArtifactsTest {
  @Test
  void committedCollectionMatchesExecutableJavaPlan() throws Exception {
    var collection = DeliveryArtifacts.collection();
    var environment = DeliveryArtifacts.environment();
    if (Boolean.getBoolean("auxiruta.export")) {
      DeliveryArtifacts.write("AuxiRuta.postman_collection.json", collection);
      DeliveryArtifacts.write("Local.postman_environment.json", environment);
    }
    assertEquals(
        collection,
        JSON.readTree(
            Files.readString(
                DeliveryArtifacts.root().resolve("docs/api/AuxiRuta.postman_collection.json"))));
    assertEquals(
        environment,
        JSON.readTree(
            Files.readString(
                DeliveryArtifacts.root().resolve("docs/api/Local.postman_environment.json"))));
    assertEquals(47, DeliveryArtifacts.steps().size());
    int requests = 0;
    for (var folder : collection.path("item")) requests += folder.path("item").size();
    assertEquals(47, requests);
  }
}

package com.auxiruta.assistance.routing;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.util.LinkedMultiValueMap;
import com.fasterxml.jackson.databind.JsonNode;
@Component
public class FleetClient {
 public record Candidate(UUID towTruckId,UUID operatorId,String operatorSubject,BigDecimal capacityKg,Set<String> supportedTypes,boolean supportsNonRollable,String qualificationCategory,Instant qualificationValidFrom,Instant qualificationValidUntil,Instant availableSince,BigDecimal latitude,BigDecimal longitude,Instant locationUpdatedAt,double straightDistanceM) {}
 private final RestClient http;private final String base,tokenUrl,clientId,secret;private final Clock clock;
 private String token;private Instant expires=Instant.EPOCH;
 public FleetClient(@Value("${FLEET_URL:http://localhost:8081}") String base,@Value("${KEYCLOAK_TOKEN_URI:http://localhost:8080/realms/auxiruta/protocol/openid-connect/token}") String tokenUrl,@Value("${KEYCLOAK_CLIENT_ID:assistance-service}") String clientId,@Value("${KEYCLOAK_CLIENT_SECRET:}") String secret,Clock clock) {
  this.base=base;this.tokenUrl=tokenUrl;this.clientId=clientId;this.secret=secret;this.clock=clock;
  var factory=new JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(java.net.http.HttpClient.Redirect.NEVER).build());factory.setReadTimeout(Duration.ofSeconds(5));http=RestClient.builder().requestFactory(factory).build();
 }
 private synchronized String token() {
  if(token!=null && expires.isAfter(clock.instant().plusSeconds(10)))return token;
  var form=new LinkedMultiValueMap<String,String>();form.add("grant_type","client_credentials");form.add("client_id",clientId);form.add("client_secret",secret);
  JsonNode response=http.post().uri(tokenUrl).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(JsonNode.class);
  if(response==null || response.path("access_token").asText().isBlank())throw new IllegalStateException("FLEET_AUTH_UNAVAILABLE");
  token=response.path("access_token").asText();expires=clock.instant().plusSeconds(response.path("expires_in").asLong(0));return token;
 }
 public List<Candidate> candidates(BigDecimal latitude,BigDecimal longitude,String type,BigDecimal weight,boolean nonRollable) {
  try {
   var uri=org.springframework.web.util.UriComponentsBuilder.fromUriString(base+"/internal/fleet/candidates").queryParam("latitude",latitude).queryParam("longitude",longitude).queryParam("vehicleType",type).queryParam("weightKg",weight).queryParam("nonRollable",nonRollable).build().toUri();
   List<Candidate> result=http.get().uri(uri).header(HttpHeaders.AUTHORIZATION,"Bearer "+token()).retrieve().body(new ParameterizedTypeReference<List<Candidate>>(){});
   if(result==null || result.size()>5)throw new IllegalStateException("FLEET_INVALID_RESPONSE");return result;
  }catch(Exception e){throw new IllegalStateException("FLEET_UNAVAILABLE");}
 }
}

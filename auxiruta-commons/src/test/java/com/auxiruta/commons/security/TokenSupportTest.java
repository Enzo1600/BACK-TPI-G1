package com.auxiruta.commons.security;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
class TokenSupportTest {
 private Jwt token(String issuer, String audience, String client, String role, Instant expiry) {
  return Jwt.withTokenValue("test").header("alg","RS256").issuer(issuer).subject("owner")
   .issuedAt(Instant.now().minusSeconds(600)).expiresAt(expiry).audience(List.of(audience))
   .claim("azp",client).claim("realm_access",Map.of("roles",List.of(role))).build();
 }
 private Jwt human(String role) { return token("issuer","auxiruta-api","auxiruta-api",role,Instant.now().plusSeconds(300)); }
 @Test void correctAudienceAndIssuerAccepted() { assertFalse(TokenSupport.validator("issuer",Set.of("auxiruta-api")).validate(human("DRIVER")).hasErrors()); }
 @Test void wrongIssuerRejected() { assertTrue(TokenSupport.validator("other",Set.of("auxiruta-api")).validate(human("DRIVER")).hasErrors()); }
 @Test void wrongAudienceRejected() { assertTrue(TokenSupport.validator("issuer",Set.of("auxiruta-fleet")).validate(human("DRIVER")).hasErrors()); }
 @Test void expiredRejected() { assertTrue(TokenSupport.validator("issuer",Set.of("auxiruta-api")).validate(token("issuer","auxiruta-api","auxiruta-api","DRIVER",Instant.now().minusSeconds(120))).hasErrors()); }
 @Test void adminCannotImpersonateDriver() { assertFalse(TokenSupport.permits(human("ADMIN"),false,"DRIVER")); }
 @Test void driverAllowed() { assertTrue(TokenSupport.permits(human("DRIVER"),false,"DRIVER")); }
 @Test void technicalClientSeparated() {
  var t=token("issuer","auxiruta-fleet","assistance-service","FLEET_SERVICE",Instant.now().plusSeconds(300));
  assertTrue(TokenSupport.permits(t,true,"FLEET_SERVICE")); assertFalse(TokenSupport.permits(t,false,"FLEET_SERVICE"));
  assertFalse(TokenSupport.permits(human("FLEET_SERVICE"),true,"FLEET_SERVICE"));
 }
 @Test void wrongAuthorizedPartyRejected() { assertFalse(TokenSupport.permits(token("issuer","auxiruta-api","other","DRIVER",Instant.now().plusSeconds(300)),false,"DRIVER")); }
 @Test void unknownRolesIgnored() { assertTrue(TokenSupport.roles(human("SUPERUSER")).isEmpty()); }
 @Test void ownerAllowedAndForeignResourceHidden() {
  assertDoesNotThrow(()->TokenSupport.requireOwner(human("DRIVER"),"owner"));
  var e=assertThrows(ResponseStatusException.class,()->TokenSupport.requireOwner(human("DRIVER"),"other"));
  assertEquals(404,e.getStatusCode().value());
 }
 @Test void missingAudienceRejectedWithoutException() {
  var t=Jwt.withTokenValue("test").header("alg","RS256").issuer("issuer").subject("owner").expiresAt(Instant.now().plusSeconds(300)).build();
  assertTrue(TokenSupport.validator("issuer",Set.of("auxiruta-api")).validate(t).hasErrors());
  assertFalse(TokenSupport.permits(t,false,"DRIVER"));
 }
 @Test void missingExpiryOrSubjectRejected() {
  var t=Jwt.withTokenValue("test").header("alg","RS256").issuer("issuer").audience(List.of("auxiruta-api")).build();
  assertTrue(TokenSupport.validator("issuer",Set.of("auxiruta-api")).validate(t).hasErrors());
 }
}

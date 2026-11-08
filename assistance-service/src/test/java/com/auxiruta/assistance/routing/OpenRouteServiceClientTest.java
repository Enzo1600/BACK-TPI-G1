package com.auxiruta.assistance.routing;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
import static com.auxiruta.assistance.routing.RouteProvider.*;
class OpenRouteServiceClientTest {
 HttpServer server;RoutingBudget budget;OpenRouteServiceClient client;
 int status;String response;String retry;AtomicInteger calls;ObjectMapper mapper=new ObjectMapper();
 Clock clock=Clock.fixed(Instant.parse("2026-11-08T00:00:00Z"),ZoneOffset.UTC);
 Point from=new Point(new BigDecimal("-31.4167"),new BigDecimal("-64.1833")),to=new Point(new BigDecimal("-31.42"),new BigDecimal("-64.19"));
 @BeforeEach void setup() throws Exception {
  budget=mock(RoutingBudget.class);when(budget.reserve()).thenReturn(true);status=200;calls=new AtomicInteger();
  response="{\"features\":[{\"properties\":{\"summary\":{\"distance\":1736.6,\"duration\":314.6},\"way_points\":[0,1]},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[[-64.1833,-31.4167],[-64.19,-31.42]]}}]}";
  server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/directions/driving-car/geojson",x->{calls.incrementAndGet();assertEquals("test-key",x.getRequestHeaders().getFirst("Authorization"));var body=mapper.readTree(x.getRequestBody());assertEquals(-64.1833,body.path("coordinates").get(0).get(0).asDouble());assertEquals("recommended",body.path("preference").asText());if(retry!=null)x.getResponseHeaders().add("Retry-After",retry);byte[] bytes=response.getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().add("Content-Type","application/json");x.sendResponseHeaders(status,bytes.length);x.getResponseBody().write(bytes);x.close();});server.start();
  client=new OpenRouteServiceClient("http://127.0.0.1:"+server.getAddress().getPort(),"test-key",budget,clock,mapper);
 }
 @AfterEach void stop(){server.stop(0);}
 @Test void parsesMetricRouteAndCachesSameRevision(){var route=client.route(from,to,"v1",300,100);assertEquals(new BigDecimal("1736.6"),route.distanceM());assertEquals(new BigDecimal("314.6"),route.durationSeconds());assertEquals(route,client.route(from,to,"v1",300,100));assertEquals(1,calls.get());client.route(from,to,"v2",300,100);assertEquals(2,calls.get());}
 @Test void noRouteIsDomainFailure(){status=404;response="{\"error\":{\"code\":2009}}";var e=assertThrows(RoutingFailure.class,()->client.route(from,to,"v1",300,100));assertTrue(e.domainRejection());}
 @Test void unknownServerFailureIsNotDomainRejection(){status=503;response="{\"error\":{\"code\":2009}}";assertFalse(assertThrows(RoutingFailure.class,()->client.route(from,to,"v1",300,100)).domainRejection());}
 @Test void quotaRespectsRetryAfterWithoutImmediateRetry(){status=429;retry="120";assertEquals(RoutingFailure.Reason.RATE_LIMIT,assertThrows(RoutingFailure.class,()->client.route(from,to,"v1",300,100)).reason());verify(budget).defer(clock.instant().plusSeconds(120));assertEquals(1,calls.get());}
 @Test void dailyQuotaDiffersFromInvalidCredentials(){status=403;response="{\"error\":\"Quota exceeded\"}";assertEquals(RoutingFailure.Reason.DAILY_QUOTA,assertThrows(RoutingFailure.class,()->client.route(from,to,"v1",300,100)).reason());response="{\"error\":\"Invalid API key\"}";assertEquals(RoutingFailure.Reason.CONFIGURATION,assertThrows(RoutingFailure.class,()->client.route(from,to,"v1",300,100)).reason());}
 @Test void localBudgetBlocksNetwork(){when(budget.reserve()).thenReturn(false);assertThrows(RoutingFailure.class,()->client.route(from,to,"v1",300,100));assertEquals(0,calls.get());}
 @Test void malformedSuccessRemainsPending(){response="{}";assertEquals(RoutingFailure.Reason.INVALID_RESPONSE,assertThrows(RoutingFailure.class,()->client.route(from,to,"v1",300,100)).reason());}
 @Test void excessiveSnapIsRejected(){response=response.replace("-64.1833,-31.4167","0,0");assertEquals(RoutingFailure.Reason.SNAP_TOO_FAR,assertThrows(RoutingFailure.class,()->client.route(from,to,"v1",300,100)).reason());}
 @Test void missingKeyDoesNotConsumeQuota(){var empty=new OpenRouteServiceClient("http://127.0.0.1:"+server.getAddress().getPort(),"",budget,clock,mapper);assertThrows(RoutingFailure.class,()->empty.route(from,to,"v1",300,100));verifyNoInteractions(budget);}
}

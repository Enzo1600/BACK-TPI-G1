package com.auxiruta.gateway.config;

import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;
import org.slf4j.LoggerFactory;
@Component
@Order(-200)
public class RequestLoggingFilter implements WebFilter {
    public Mono<Void> filter(ServerWebExchange x, WebFilterChain chain) {
        long start=System.nanoTime();
        return chain.filter(x).doFinally(signal -> LoggerFactory.getLogger(getClass()).info("HTTP method={} status={} durationMs={}",x.getRequest().getMethod(),x.getResponse().getStatusCode(),(System.nanoTime()-start)/1_000_000));
    }
}

package com.auxiruta.assistance.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.LoggerFactory;
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        long start = System.nanoTime();
        try { chain.doFilter(req,res); }
        finally { LoggerFactory.getLogger(getClass()).debug("HTTP method={} status={} durationMs={}",req.getMethod(),res.getStatus(),(System.nanoTime()-start)/1_000_000); }
    }
}

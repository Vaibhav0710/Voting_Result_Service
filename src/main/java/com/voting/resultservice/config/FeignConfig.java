package com.voting.resultservice.config;

import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Feign setup. Outgoing calls carry the incoming request's {@code Authorization} header, so a call made
 * on behalf of an ADMIN (e.g. the audit trail) is authorised as that ADMIN by the Voting Service.
 * Kept off the application class so web-slice tests don't need Feign.
 */
@Configuration
@EnableFeignClients(basePackages = "com.voting.resultservice.client")
public class FeignConfig {

    @Bean
    public RequestInterceptor forwardAuthorizationHeader() {
        return template -> {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
                HttpServletRequest request = attributes.getRequest();
                String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
                if (authorization != null) {
                    template.header(HttpHeaders.AUTHORIZATION, authorization);
                }
            }
        };
    }
}

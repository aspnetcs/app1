package com.webchat.platformapi.common.filter;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RequestIdFilterConfig {

    @Bean
    public FilterRegistrationBean<RequestIdFilter> requestIdFilterRegistration() {
        FilterRegistrationBean<RequestIdFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new RequestIdFilter());
        reg.addUrlPatterns("/*");
        reg.setOrder(0); // before JwtAuthFilter (order=1)
        return reg;
    }
}



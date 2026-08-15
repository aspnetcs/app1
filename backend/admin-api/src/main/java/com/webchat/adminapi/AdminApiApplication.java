package com.webchat.adminapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = {"com.webchat.adminapi", "com.webchat.platformapi"})
@EntityScan(basePackages = "com.webchat.platformapi")
@EnableJpaRepositories(basePackages = "com.webchat.platformapi")
@EnableScheduling
@ComponentScan(basePackages = {"com.webchat.adminapi", "com.webchat.platformapi"})
public class AdminApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApiApplication.class, args);
    }
}

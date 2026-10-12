package com.pollpulse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Entry point. Running main() starts Spring Boot and the Tomcat web server.
@SpringBootApplication
public class PollPulseApplication {

    public static void main(String[] args) {
        SpringApplication.run(PollPulseApplication.class, args);
    }
}

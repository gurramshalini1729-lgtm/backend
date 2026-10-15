package com.pollpulse.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "PollPulse backend is running on port 1234!";
    }

    @GetMapping("/api/health")
    public String health() {
        return "OK";
    }
}

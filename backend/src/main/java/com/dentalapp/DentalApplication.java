package com.dentalapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@org.springframework.scheduling.annotation.EnableScheduling
@SpringBootApplication
public class DentalApplication {
    public static void main(String[] args) {
        SpringApplication.run(DentalApplication.class, args);
    }
}

package com.suivia;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync // Para processamento simulado de mensageria
public class SuiviaApplication {
    public static void main(String[] args) {
        SpringApplication.run(SuiviaApplication.class, args);
    }
}

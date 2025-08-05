package it.eng.dome.revenue.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication
@EnableScheduling
public class RevenueEngineApplication {

    public static void main(String[] args) {
    	SpringApplication.run(RevenueEngineApplication.class, args);
    }

}

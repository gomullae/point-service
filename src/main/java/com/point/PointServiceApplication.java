package com.point;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@EnableRetry
@SpringBootApplication
public class PointServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(PointServiceApplication.class, args);
	}

}

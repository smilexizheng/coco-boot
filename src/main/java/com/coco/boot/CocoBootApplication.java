package com.coco.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class CocoBootApplication {

	public static void main(String[] args) {
		SpringApplication.run(CocoBootApplication.class, args);
	}

}

package com.beyond.wbs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class MasterApplication {
	public static void main(String[] args) {
		SpringApplication.run(MasterApplication.class, args);
	}
}

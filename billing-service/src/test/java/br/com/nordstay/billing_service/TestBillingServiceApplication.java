package br.com.nordstay.billing_service;

import org.springframework.boot.SpringApplication;

public class TestBillingServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(BillingServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}

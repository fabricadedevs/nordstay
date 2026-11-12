package br.com.nordstay.door_lock_service;

import org.springframework.boot.SpringApplication;

public class TestDoorLockServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(DoorLockServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}

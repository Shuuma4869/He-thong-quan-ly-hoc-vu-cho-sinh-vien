package vn.edu.phenikaa.ams;

import org.springframework.boot.SpringApplication;

public class TestAmsApplication {

	public static void main(String[] args) {
		SpringApplication.from(AmsApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}

package com.streamhelper.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class StreamHelperApplication {

	public static void main(String[] args) {
		SpringApplication.run(StreamHelperApplication.class, args);
	}

}

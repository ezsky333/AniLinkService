package xyz.ezsky.anilink;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import xyz.ezsky.anilink.service.MediaScannerService;

@SpringBootApplication
public class AniLinkServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AniLinkServiceApplication.class, args);
	}

	@Autowired
	private MediaScannerService mediaScannerService;

	@Bean
	public CommandLineRunner startup() {
		return args -> {
			mediaScannerService.scanAllLibraries();
		};
	}
}

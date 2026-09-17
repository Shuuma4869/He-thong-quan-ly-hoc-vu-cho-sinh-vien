package vn.edu.phenikaa.ams.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI amsOpenApi() {
        return new OpenAPI().info(new Info()
                .title("AMS API")
                .description("API của Hệ thống quản lý học vụ cho sinh viên")
                .version("bootstrap"));
    }
}

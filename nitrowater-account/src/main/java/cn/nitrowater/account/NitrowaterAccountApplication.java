package cn.nitrowater.account;

import cn.nitrowater.core.web.ApiResponseAdvice;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {"cn.nitrowater.account", "cn.nitrowater.core"},
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "cn\\.nitrowater\\.core\\.web\\..*"))
@Import(ApiResponseAdvice.class)
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
@EntityScan(basePackages = "cn.nitrowater.core.domain")
@EnableJpaRepositories(basePackages = "cn.nitrowater.core.infrastructure.persistence")
public class NitrowaterAccountApplication {

    public static void main(String[] args) {
        SpringApplication.run(NitrowaterAccountApplication.class, args);
    }

}

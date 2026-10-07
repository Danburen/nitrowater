package cn.nitrowater.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "cn.nitrowater")
@EntityScan(basePackages = "cn.nitrowater.core.lib.entity")
@EnableJpaRepositories(basePackages = "cn.nitrowater.core.lib.infrastructure.persistence")
public class NitrowaterAccountApplication {

    public static void main(String[] args) {
        SpringApplication.run(NitrowaterAccountApplication.class, args);
    }

}

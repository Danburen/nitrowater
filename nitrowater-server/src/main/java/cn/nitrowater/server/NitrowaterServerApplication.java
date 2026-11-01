package cn.nitrowater.server;

import org.springframework.boot.SpringApplication;
import cn.nitrowater.core.web.ApiResponseAdvice;
import cn.nitrowater.core.web.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
@Import({ApiResponseAdvice.class, GlobalExceptionHandler.class})
@EnableScheduling
public class NitrowaterServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(NitrowaterServerApplication.class, args);
    }

}

package cn.nitrowater.server;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Phase 1：上下文依赖外部 MySQL/Redis，本模块尚未接入配置，暂不跑上下文测试")
class NitrowaterServerApplicationTests {

    @Test
    void contextLoads() {
    }

}

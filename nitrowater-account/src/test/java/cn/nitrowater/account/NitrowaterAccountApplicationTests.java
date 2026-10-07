package cn.nitrowater.account;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Phase 1：上下文依赖外部 MySQL/Redis/JWT 密钥，改用 Bin/start-account.bat (bootRun) 手测")
class NitrowaterAccountApplicationTests {

    @Test
    void contextLoads() {
    }

}

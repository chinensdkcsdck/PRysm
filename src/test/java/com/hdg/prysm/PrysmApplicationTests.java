package com.hdg.prysm;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "prysm.runner.enabled=false")
class PrysmApplicationTests {

    /**
     * 验证 Spring 上下文可以在关闭 Runner 的情况下启动。
     */
    @Test
    void contextLoads() {
    }

}

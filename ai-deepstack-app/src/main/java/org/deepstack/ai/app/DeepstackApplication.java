package org.deepstack.ai.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 单体应用入口（扫描 {@code org.deepstack.ai}）。
 */
@SpringBootApplication(scanBasePackages = "org.deepstack.ai")
@EnableScheduling
public class DeepstackApplication {

    /** 应用入口。 */
    public static void main(String[] args) {
        SpringApplication.run(DeepstackApplication.class, args);
    }
}

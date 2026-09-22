package cn.studykid.growthplanet;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 成长星球后端启动类。
 */
@SpringBootApplication
@MapperScan("cn.studykid.growthplanet.mapper")
public class GrowthPlanetApplication {

    public static void main(String[] args) {
        SpringApplication.run(GrowthPlanetApplication.class, args);
    }
}

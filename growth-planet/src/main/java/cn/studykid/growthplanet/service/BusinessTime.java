package cn.studykid.growthplanet.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

@Component
public class BusinessTime {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    public LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }
}

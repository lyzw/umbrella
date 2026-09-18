package cn.studykid.growthplanet.dto.response;

import java.time.LocalDate;

public record AllowanceRuleResp(String childId, String singleLimit, String dailyLimit, String weeklyLimit,
        String dailyUsed, String weeklyUsed, LocalDate dailyPeriod, LocalDate weeklyPeriod, int version) {
}

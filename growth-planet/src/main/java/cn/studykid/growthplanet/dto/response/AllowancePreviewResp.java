package cn.studykid.growthplanet.dto.response;

import java.time.LocalDate;

public record AllowancePreviewResp(String confirmId, int confirmVersion, int walletVersion,
        int ruleVersion, LocalDate usageDate, String totalAmount, String balance, String singleLimit,
        String dailyUsed, String dailyLimit, String weeklyUsed, String weeklyLimit,
        boolean requiresExplicitConfirm) {
}

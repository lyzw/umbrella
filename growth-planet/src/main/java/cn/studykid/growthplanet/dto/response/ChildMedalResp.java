package cn.studykid.growthplanet.dto.response;

public record ChildMedalResp(MedalDefinitionResp definition, boolean earned, String awardedAt,
                            int consecutiveCount, int progress, int threshold) {
}

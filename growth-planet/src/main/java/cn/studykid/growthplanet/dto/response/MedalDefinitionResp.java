package cn.studykid.growthplanet.dto.response;

public record MedalDefinitionResp(String definitionId, String code, String name, String description,
                                  String icon, String category, String conditionType, int threshold, int sortOrder) {
}

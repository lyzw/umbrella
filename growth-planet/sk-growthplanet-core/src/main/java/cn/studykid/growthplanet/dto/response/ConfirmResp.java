package cn.studykid.growthplanet.dto.response;

import java.time.LocalDate;
import java.util.List;

public record ConfirmResp(String confirmId, String childId, String menuId, String previousConfirmId,
        String status, int version, LocalDate menuDate, String mealType, boolean isOverLimit,
        String totalAmount, String estBalanceAfter, String balance, Integer walletVersion,
        List<Item> items, String childVisibleNote, List<Suggestion> suggestedItems) {
    // sourceType 标记菜品来源（PRESET/FAMILY），供儿童端重新提报/家长建议时重建 dishRef。
    public record Item(String dishId, String sourceType, String dishName, int quantity, String unitPrice,
            String subtotal, String note) {
    }
    public record Suggestion(String dishId, String sourceType, int quantity, String note) {
    }
}

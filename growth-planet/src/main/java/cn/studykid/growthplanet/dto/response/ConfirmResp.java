package cn.studykid.growthplanet.dto.response;

import java.time.LocalDate;
import java.util.List;

public record ConfirmResp(String confirmId, String childId, String menuId, String previousConfirmId,
        String status, int version, LocalDate menuDate, String mealType, boolean isOverLimit,
        String totalAmount, String estBalanceAfter, String balance, Integer walletVersion,
        List<Item> items, String childVisibleNote, List<Suggestion> suggestedItems) {
    public record Item(String dishId, String dishName, int quantity, String unitPrice, String subtotal, String note) {
    }
    public record Suggestion(String dishId, int quantity, String note) {
    }
}

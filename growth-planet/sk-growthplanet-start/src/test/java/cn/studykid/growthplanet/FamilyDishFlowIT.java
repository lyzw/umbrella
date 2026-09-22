package cn.studykid.growthplanet;

import cn.studykid.growthplanet.common.enums.RoleEnum;
import cn.studykid.growthplanet.dto.response.FamilyDishResp;
import cn.studykid.growthplanet.entity.*;
import cn.studykid.growthplanet.mapper.*;
import cn.studykid.growthplanet.service.BusinessTime;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 家庭私有菜品（F-01~F-11，C-01~C-06）集成测试。
 * 覆盖：CRUD + 乐观锁 + 上下架、跨家庭隔离、预置/私有混合菜单的 sourceType 标记、
 * 引用删除后菜单缺失、以及端到端确认下单对私有菜品扣款并记录 source_type。
 */
class FamilyDishFlowIT extends BaseIT {
    @Autowired MenuDailyMapper menus;
    @Autowired FamilyDishMapper familyDishes;
    @Autowired DishMapper dishes;
    @Autowired MenuConfirmMapper confirms;
    @Autowired MenuItemMapper itemsMapper;
    @Autowired WalletMapper wallets;
    @Autowired BusinessTime time;

    @Test
    void parentCanCreateListUpdateToggleAndVersionFamilyDish() throws Exception {
        var ctx = readyProfile();
        String admin = adminToken();
        long categoryId = category(admin);
        long id = createFamilyDish(ctx.parentToken(), categoryId);

        JsonNode list = data(get("/api/mini/parent/family-dish").header("Authorization", bearer(ctx.parentToken()))
                .param("pageSize", "20"));
        assertEquals(1, list.get("total").asInt());
        assertEquals(id, list.get("items").get(0).get("dishId").asLong());
        assertEquals("ON_SALE", list.get("items").get(0).get("status").asText());

        FamilyDishResp detail = data(mockMvc.perform(get("/api/mini/parent/family-dish/{id}", id)
                .header("Authorization", bearer(ctx.parentToken()))), FamilyDishResp.class);
        assertEquals("ON_SALE", detail.getStatus());
        assertEquals(0, detail.getVersion());

        FamilyDishResp updated = updateFamilyDish(ctx.parentToken(), id, 0, categoryId);
        assertEquals("妈妈改名菜", updated.getName());
        assertEquals("9.00", updated.getVirtualPrice());
        assertEquals(1, updated.getVersion());

        FamilyDishResp off = changeStatus(ctx.parentToken(), id, 1, "OFF_SALE");
        assertEquals("OFF_SALE", off.getStatus());
        assertEquals(2, off.getVersion());

        FamilyDishResp on = changeStatus(ctx.parentToken(), id, 2, "ON_SALE");
        assertEquals("ON_SALE", on.getStatus());
        assertEquals(3, on.getVersion());

        // 乐观锁：版本不匹配应冲突
        mockMvc.perform(put("/api/mini/parent/family-dish/{id}", id).header("Authorization", bearer(ctx.parentToken()))
                .param("expectedVersion", "99").contentType(JSON)
                .content(json(familyDishBody(categoryId, "冲突菜", "1.00", List.of(), "DECLARED"))))
                .andExpect(status().isConflict());
    }

    @Test
    void parentCanListEnabledCategoriesForDishForm() throws Exception {
        var ctx = readyProfile();
        String admin = adminToken();
        long categoryId = category(admin);

        JsonNode list = data(get("/api/mini/parent/dish-category").header("Authorization", bearer(ctx.parentToken())));
        assertTrue(list.isArray());
        boolean found = false;
        for (JsonNode node : list) {
            if (node.get("categoryId").asLong() == categoryId) {
                found = true;
            }
        }
        assertTrue(found, "家长应能读取预置分类用于私有菜品录入");

        mockMvc.perform(get("/api/mini/parent/dish-category").header("Authorization", bearer(ctx.childToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    void nonParentCannotManageFamilyDishes() throws Exception {
        var ctx = readyProfile();
        String admin = adminToken();
        long categoryId = category(admin);
        long id = createFamilyDish(ctx.parentToken(), categoryId);
        for (String token : List.of(ctx.childToken(), admin)) {
            mockMvc.perform(post("/api/mini/parent/family-dish").header("Authorization", bearer(token))
                    .contentType(JSON).content(json(familyDishBody(categoryId, "x", "1.00", List.of(), "DECLARED"))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/mini/parent/family-dish").header("Authorization", bearer(token)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/mini/parent/family-dish/{id}", id).header("Authorization", bearer(token)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(put("/api/mini/parent/family-dish/{id}", id).header("Authorization", bearer(token))
                    .param("expectedVersion", "0").contentType(JSON)
                    .content(json(familyDishBody(categoryId, "x", "1.00", List.of(), "DECLARED"))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/mini/parent/family-dish/{id}/status", id).header("Authorization", bearer(token))
                    .param("targetStatus", "OFF_SALE").param("expectedVersion", "0"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/mini/parent/family-dish/{id}", id).header("Authorization", bearer(token))
                    .param("expectedVersion", "0")).andExpect(status().isForbidden());
        }
    }

    @Test
    void familyDishesAreIsolatedAcrossFamilies() throws Exception {
        var ctx = readyProfile();
        String admin = adminToken();
        long categoryId = category(admin);
        long id = createFamilyDish(ctx.parentToken(), categoryId);
        var other = readyProfile();

        JsonNode otherList = data(get("/api/mini/parent/family-dish").header("Authorization", bearer(other.parentToken()))
                .param("pageSize", "20"));
        assertEquals(0, otherList.get("total").asInt());
        mockMvc.perform(get("/api/mini/parent/family-dish/{id}", id).header("Authorization", bearer(other.parentToken())))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/mini/parent/family-dish/{id}", id).header("Authorization", bearer(other.parentToken()))
                .param("expectedVersion", "0").contentType(JSON)
                .content(json(familyDishBody(categoryId, "劫持", "1.00", List.of(), "DECLARED"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/mini/parent/family-dish/{id}", id).header("Authorization", bearer(other.parentToken()))
                .param("expectedVersion", "0")).andExpect(status().isNotFound());
        // 隔离已由上面的 404 断言证明：另一家庭既不能查看也不能改动本家庭的菜品
    }

    @Test
    void mixedMenuCombinesPresetAndFamilyDishesWithSourceType() throws Exception {
        var ctx = readyProfile();
        String admin = adminToken();
        long categoryId = category(admin);
        long preset = dish(admin, categoryId, "白米饭", List.of(), "DECLARED");
        long family = createFamilyDish(ctx.parentToken(), categoryId);
        LocalDate date = today();

        long menuId = publishMenu(ctx.parentToken(),
                List.of(refBody(preset), refFamilyBody(family)), date);

        JsonNode maint = data(get("/api/mini/parent/menu-daily").header("Authorization", bearer(ctx.parentToken()))
                .param("menuDate", date.toString()).param("mealType", "LUNCH"));
        assertEquals(menuId, maint.get("menuId").asLong());
        List<String> types = new ArrayList<>();
        for (JsonNode d : maint.get("dishes")) {
            types.add(d.get("sourceType").asText());
        }
        assertTrue(types.contains("PRESET"));
        assertTrue(types.contains("FAMILY"));

        JsonNode view = daily(ctx.childToken(), childUserId(ctx).toString(), date);
        assertEquals(2, view.get("dishes").size());
        for (JsonNode d : view.get("dishes")) {
            assertEquals("DECLARED", d.get("safetyStatus").asText());
            assertTrue(d.get("canSelect").asBoolean());
        }
    }

    @Test
    void deletingReferencedFamilyDishLeavesMenuMarkedMissing() throws Exception {
        var ctx = readyProfile();
        String admin = adminToken();
        long categoryId = category(admin);
        long preset = dish(admin, categoryId, "白米饭", List.of(), "DECLARED");
        long family = createFamilyDish(ctx.parentToken(), categoryId);
        LocalDate date = today();
        publishMenu(ctx.parentToken(), List.of(refBody(preset), refFamilyBody(family)), date);

        assertEquals(1, references(ctx.parentToken(), family));
        deleteFamilyDish(ctx.parentToken(), family, 0);
        // 软删除后详情不可见
        mockMvc.perform(get("/api/mini/parent/family-dish/{id}", family).header("Authorization", bearer(ctx.parentToken())))
                .andExpect(status().isNotFound());
        // 菜单仍引用该菜，但维护页应标记为缺失
        JsonNode maint = data(get("/api/mini/parent/menu-daily").header("Authorization", bearer(ctx.parentToken()))
                .param("menuDate", date.toString()).param("mealType", "LUNCH"));
        boolean missing = false;
        for (JsonNode m : maint.get("missingDishIds")) {
            if (m.asLong() == family) {
                missing = true;
            }
        }
        assertTrue(missing);
    }

    @Test
    void familyDishOrderFlowDebitsWalletAndRecordsSourceType() throws Exception {
        var ctx = readyProfile();
        grantMoney(ctx, "50.00", "init");
        String admin = adminToken();
        long categoryId = category(admin);
        long preset = dish(admin, categoryId, "白米饭", List.of(), "DECLARED");
        long family = createFamilyDish(ctx.parentToken(), categoryId);
        LocalDate date = today();
        long menuId = publishMenu(ctx.parentToken(),
                List.of(refBody(preset), refFamilyBody(family)), date);

        long child = childUserId(ctx);
        var items = List.of(
                Map.of("dishRef", refBody(preset), "quantity", 1),
                Map.of("dishRef", refFamilyBody(family), "quantity", 1));
        JsonNode submitted = submitConfirm(ctx.childToken(), menuId, items);
        long confirmId = submitted.get("confirmId").asLong();
        assertEquals("25.00", submitted.get("totalAmount").asText());
        // 确认单条目回传 sourceType，供儿童端重新提报/家长建议重建 dishRef
        boolean hasPresetSource = false;
        boolean hasFamilySource = false;
        for (JsonNode entry : submitted.get("items")) {
            if ("PRESET".equals(entry.get("sourceType").asText())) {
                hasPresetSource = true;
            }
            if ("FAMILY".equals(entry.get("sourceType").asText())) {
                hasFamilySource = true;
            }
        }
        assertTrue(hasPresetSource);
        assertTrue(hasFamilySource);

        approveConfirm(ctx.parentToken(), confirmId);
        // 钱包扣款：50 - (18 + 7) = 25
        assertEquals(new BigDecimal("25.00"), balance(child));

        List<MenuItem> lines = itemsMapper.selectList(new QueryWrapper<MenuItem>().eq("confirm_id", confirmId));
        assertEquals(2, lines.size());
        assertTrue(lines.stream().anyMatch(l -> "FAMILY".equals(l.getSourceType()) && family == l.getDishId()));
        assertTrue(lines.stream().anyMatch(l -> "PRESET".equals(l.getSourceType()) && preset == l.getDishId()));
    }

    // ---------- 私有菜品辅助 ----------

    private long createFamilyDish(String token, long categoryId) throws Exception {
        var body = familyDishBody(categoryId, "妈妈拿手菜", "7.00", List.of(), "DECLARED");
        return id(mockMvc.perform(post("/api/mini/parent/family-dish").header("Authorization", bearer(token))
                .contentType(JSON).content(json(body))).andExpect(status().isOk()).andReturn(), "dishId");
    }

    private FamilyDishResp updateFamilyDish(String token, long id, int expectedVersion, long categoryId) throws Exception {
        var body = familyDishBody(categoryId, "妈妈改名菜", "9.00", List.of(), "DECLARED");
        return data(mockMvc.perform(put("/api/mini/parent/family-dish/{id}", id).header("Authorization", bearer(token))
                .param("expectedVersion", String.valueOf(expectedVersion)).contentType(JSON).content(json(body)))
                .andExpect(status().isOk()), FamilyDishResp.class);
    }

    private FamilyDishResp changeStatus(String token, long id, int expectedVersion, String target) throws Exception {
        return data(mockMvc.perform(post("/api/mini/parent/family-dish/{id}/status", id).header("Authorization", bearer(token))
                .param("targetStatus", target).param("expectedVersion", String.valueOf(expectedVersion)))
                .andExpect(status().isOk()), FamilyDishResp.class);
    }

    private int references(String token, long id) throws Exception {
        JsonNode node = data(mockMvc.perform(get("/api/mini/parent/family-dish/{id}/references", id)
                .header("Authorization", bearer(token))).andExpect(status().isOk()));
        return node.get("menuCount").asInt();
    }

    private void deleteFamilyDish(String token, long id, int expectedVersion) throws Exception {
        mockMvc.perform(delete("/api/mini/parent/family-dish/{id}", id).header("Authorization", bearer(token))
                .param("expectedVersion", String.valueOf(expectedVersion))).andExpect(status().isOk());
    }

    private long publishMenu(String token, List<Map<String, Object>> refs, LocalDate date) throws Exception {
        var body = new LinkedHashMap<>(Map.of("menuDate", date.toString(), "mealType", "LUNCH",
                "dishIds", refs, "status", "PUBLISHED"));
        return id(mockMvc.perform(post("/api/mini/parent/menu-daily").header("Authorization", bearer(token))
                .contentType(JSON).content(json(body))).andExpect(status().isOk()).andReturn(), "menuId");
    }

    private JsonNode daily(String token, String childId, LocalDate date) throws Exception {
        return data(mockMvc.perform(get("/api/mini/menu/daily").header("Authorization", bearer(token))
                .param("sourceType", "FAMILY").param("menuDate", date.toString())
                .param("mealType", "LUNCH").param("childId", childId)).andExpect(status().isOk()));
    }

    private JsonNode submitConfirm(String token, long menuId, List<Map<String, Object>> items) throws Exception {
        var body = Map.of("menuId", Long.toString(menuId), "items", items);
        var result = mockMvc.perform(post("/api/mini/menu/confirm").header("Authorization", bearer(token))
                .header("Idempotency-Key", "family-dish-order-" + menuId)
                .contentType(JSON).content(json(body))).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private JsonNode approveConfirm(String token, long confirmId) throws Exception {
        return data(mockMvc.perform(post("/api/mini/parent/approve/{id}/approve", confirmId).header("Authorization", bearer(token))
                .contentType(JSON).content("{\"expectedVersion\":0}")).andExpect(status().isOk()));
    }

    private BigDecimal balance(long childUserId) {
        Wallet wallet = wallets.selectOne(new QueryWrapper<Wallet>().eq("child_id", childUserId));
        return wallet.getBalance();
    }

    private Map<String, Object> familyDishBody(long categoryId, String name, String price,
                                               List<String> allergens, String allergenStatus) {
        return new LinkedHashMap<>(Map.of(
                "categoryId", Long.toString(categoryId),
                "name", name,
                "virtualPrice", price,
                "allergens", allergens,
                "allergenStatus", allergenStatus,
                "spiceLevel", 0));
    }

    private Map<String, Object> refBody(long id) {
        return Map.of("type", "PRESET", "id", id);
    }

    private Map<String, Object> refFamilyBody(long id) {
        return Map.of("type", "FAMILY", "id", id);
    }

    // ---------- 上下文与目录辅助（复用 CatalogFlowIT 范式） ----------

    private FamilyContext readyProfile() throws Exception {
        var ctx = setupFamily();
        grant(ctx);
        approve(ctx);
        saveProfile(ctx);
        return ctx;
    }

    private void saveProfile(FamilyContext ctx) throws Exception {
        mockMvc.perform(post("/api/mini/child/profile").header("Authorization", bearer(ctx.parentToken()))
                .contentType(JSON).content(profileJson(ctx))).andExpect(status().isOk());
    }

    private void grantMoney(FamilyContext ctx, String amount, String key) throws Exception {
        mockMvc.perform(post("/api/mini/wallet/grant").header("Authorization", bearer(ctx.parentToken()))
                .header("Idempotency-Key", key).contentType(JSON)
                .content("{\"childId\":\"" + childUserId(ctx) + "\",\"amount\":\"" + amount
                        + "\",\"reason\":\"家庭菜品测试发放\"}")).andExpect(status().isOk());
    }

    private String adminToken() {
        User admin = new User();
        admin.setOpenid("family-dish-admin-" + UUID.randomUUID());
        admin.setRole("ADMIN");
        admin.setStatus("NORMAL");
        users.insert(admin);
        return tokenWithJti(admin.getId(), RoleEnum.ADMIN, List.of(), UUID.randomUUID().toString());
    }

    private long category(String admin) throws Exception {
        return id(mockMvc.perform(post("/api/mini/admin/dish-category").header("Authorization", bearer(admin))
                .contentType(JSON).content("{\"name\":\"家庭菜品分类\",\"sort\":0,\"status\":\"ENABLED\"}"))
                .andExpect(status().isOk()).andReturn(), "categoryId");
    }

    private long dish(String admin, long categoryId, String name, List<String> allergens, String allergenStatus)
            throws Exception {
        return id(mockMvc.perform(post("/api/mini/admin/dish").header("Authorization", bearer(admin))
                .contentType(JSON).content(json(dishBody(categoryId, name, allergens, allergenStatus))))
                .andExpect(status().isOk()).andReturn(), "dishId");
    }

    private Map<String, Object> dishBody(long categoryId, String name, List<String> allergens, String allergenStatus) {
        return new LinkedHashMap<>(Map.of("categoryId", Long.toString(categoryId), "name", name,
                "virtualPrice", "18.00", "allergens", allergens, "allergenStatus", allergenStatus,
                "spiceLevel", 0, "status", "ON_SALE"));
    }

    private JsonNode data(MockHttpServletRequestBuilder request) throws Exception {
        return objectMapper.readTree(mockMvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("data");
    }

    private JsonNode data(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("data");
    }

    private <T> T data(ResultActions result, Class<T> clazz) throws Exception {
        JsonNode data = objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("data");
        return objectMapper.convertValue(data, clazz);
    }

    private long id(MvcResult result, String field) throws Exception {
        JsonNode value = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get(field);
        assertTrue(value.isTextual(), "Business identifiers must be JSON strings");
        return Long.parseLong(value.asText());
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private LocalDate today() {
        return time.today();
    }

    @Autowired
    private UserMapper users;
}

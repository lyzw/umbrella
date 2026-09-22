package cn.studykid.growthplanet;

import cn.studykid.growthplanet.common.context.UserContext;
import cn.studykid.growthplanet.common.enums.RoleEnum;
import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.config.ComplianceProperties;
import cn.studykid.growthplanet.dto.request.OrderLineReq;
import cn.studykid.growthplanet.entity.ChildProfile;
import cn.studykid.growthplanet.entity.ChildWantEat;
import cn.studykid.growthplanet.entity.DishRef;
import cn.studykid.growthplanet.entity.Dish;
import cn.studykid.growthplanet.entity.MenuDaily;
import cn.studykid.growthplanet.entity.User;
import cn.studykid.growthplanet.mapper.ChildProfileMapper;
import cn.studykid.growthplanet.mapper.ChildWantEatMapper;
import cn.studykid.growthplanet.mapper.DishMapper;
import cn.studykid.growthplanet.mapper.MenuDailyMapper;
import cn.studykid.growthplanet.mapper.UserMapper;
import cn.studykid.growthplanet.service.BusinessTime;
import cn.studykid.growthplanet.service.CatalogService;
import cn.studykid.growthplanet.service.ChildAuthorizationService;
import cn.studykid.growthplanet.service.SessionService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CatalogFlowIT extends BaseIT {
    @Autowired CatalogService catalog;
    @Autowired ChildAuthorizationService authorization;
    @Autowired SessionService sessions;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ChildProfileMapper profiles;
    @Autowired UserMapper users;
    @Autowired DishMapper dishes;
    @Autowired MenuDailyMapper menus;
    @Autowired ChildWantEatMapper wantEats;
    @Autowired ComplianceProperties policy;
    @MockitoSpyBean BusinessTime time;

    @Test
    void adminCrudUsesStringPricesAndIdsAndPagination() throws Exception {
        String admin = adminToken();
        long categoryId = category(admin);
        long dishId = dish(admin, categoryId, "Carrot stew", List.of(), "DECLARED");
        mockMvc.perform(get("/api/admin/dish/{id}", dishId).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dishId").value(Long.toString(dishId)))
                .andExpect(jsonPath("$.data.categoryId").value(Long.toString(categoryId)))
                .andExpect(jsonPath("$.data.virtualPrice").value("18.00"));
        Map<String, Object> changed = dishBody(categoryId, "Edited", List.of("MILK"), "DECLARED");
        changed.put("virtualPrice", "0.00");
        changed.put("spiceLevel", 3);
        changed.put("status", "OFF_SALE");
        mockMvc.perform(put("/api/admin/dish/{id}", dishId).header("Authorization", bearer(admin))
                .contentType(JSON).content(json(changed))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.virtualPrice").value("0.00"));
        mockMvc.perform(get("/api/admin/dish").header("Authorization", bearer(admin))
                .param("categoryId", Long.toString(categoryId)).param("status", "OFF_SALE").param("pageSize", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].dishId").value(Long.toString(dishId)));
        mockMvc.perform(get("/api/admin/dish").header("Authorization", bearer(admin))
                .param("categoryId", Long.toString(categoryId)).param("page", "2147483647").param("pageSize", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
        mockMvc.perform(get("/api/admin/dish-category").header("Authorization", bearer(admin))
                .param("pageSize", "1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].categoryId").isString());
        for (String path : List.of("/api/admin/dish", "/api/admin/dish-category")) {
            for (String query : List.of("?page=0", "?pageSize=0", "?pageSize=101", "?page=1.5")) {
                mockMvc.perform(get(path + query).header("Authorization", bearer(admin)))
                        .andExpect(status().isBadRequest());
            }
        }
        mockMvc.perform(delete("/api/admin/dish/{id}", dishId).header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
        assertNull(dishes.selectById(dishId));
        mockMvc.perform(get("/api/admin/dish/{id}", dishId).header("Authorization", bearer(admin)))
                .andExpect(status().isNotFound());
    }

    @Test
    void dishPutClearsNullableFieldsWhenNullOrOmitted() throws Exception {
        String admin = adminToken();
        long categoryId = category(admin);
        long dishId = dish(admin, categoryId, "Optional fields", List.of(), "DECLARED");
        for (boolean explicitNull : List.of(true, false)) {
            Map<String, Object> populated = dishBody(categoryId, "Optional fields", List.of(), "DECLARED");
            populated.put("imageUrl", "https://example.com/synthetic-dish.png");
            populated.put("calories", 125);
            populated.put("tags", "synthetic");
            mockMvc.perform(put("/api/admin/dish/{id}", dishId).header("Authorization", bearer(admin))
                    .contentType(JSON).content(json(populated))).andExpect(status().isOk());
            Dish before = dishes.selectById(dishId);
            assertEquals(populated.get("imageUrl"), before.getImageUrl());
            assertEquals(125, before.getCalories());
            assertEquals("synthetic", before.getTags());

            String cleared = explicitNull ? """
                    {"categoryId":"%s","name":"Optional fields","virtualPrice":"18.00",
                     "allergens":[],"allergenStatus":"DECLARED","spiceLevel":0,"status":"ON_SALE",
                     "imageUrl":null,"calories":null,"tags":null}
                    """.formatted(categoryId)
                    : json(dishBody(categoryId, "Optional fields", List.of(), "DECLARED"));
            mockMvc.perform(put("/api/admin/dish/{id}", dishId).header("Authorization", bearer(admin))
                    .contentType(JSON).content(cleared)).andExpect(status().isOk());
            // Read persisted state: the PUT response alone can hide a NOT_NULL update strategy.
            Dish after = dishes.selectById(dishId);
            assertNull(after.getImageUrl());
            assertNull(after.getCalories());
            assertNull(after.getTags());
            assertEquals(before.getName(), after.getName());
            assertEquals(before.getVirtualPrice(), after.getVirtualPrice());
            assertEquals(before.getAllergens(), after.getAllergens());
            mockMvc.perform(get("/api/admin/dish/{id}", dishId).header("Authorization", bearer(admin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.imageUrl").doesNotExist())
                    .andExpect(jsonPath("$.data.calories").doesNotExist())
                    .andExpect(jsonPath("$.data.tags").doesNotExist())
                    .andExpect(jsonPath("$.data.virtualPrice").value("18.00"));
        }
    }

    @Test
    void rejectsNonAdminAndStrictDishCategoryInputs() throws Exception {
        String admin = adminToken();
        long categoryId = category(admin);
        long dishId = dish(admin, categoryId, "Rice", List.of(), "DECLARED");
        var ctx = setupFamily();
        for (String token : List.of(ctx.parentToken(), ctx.childToken())) {
            mockMvc.perform(post("/api/admin/dish").header("Authorization", bearer(token))
                    .contentType(JSON).content(json(dishBody(categoryId, "Rice", List.of(), "DECLARED"))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(put("/api/admin/dish/{id}", dishId).header("Authorization", bearer(token))
                    .contentType(JSON).content(json(dishBody(categoryId, "Rice", List.of(), "DECLARED"))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/admin/dish/{id}", dishId).header("Authorization", bearer(token)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/admin/dish").header("Authorization", bearer(token)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/admin/dish-category").header("Authorization", bearer(token))
                    .contentType(JSON).content("{\"name\":\"Category\"}")).andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/admin/dish")).andExpect(status().isUnauthorized());
        List<Map.Entry<String, Object>> invalid = List.of(
                Map.entry("name", " "), Map.entry("name", "x".repeat(65)),
                Map.entry("categoryId", 0), Map.entry("categoryId", Long.MAX_VALUE),
                Map.entry("virtualPrice", "-0.01"), Map.entry("virtualPrice", "1.001"),
                Map.entry("virtualPrice", "100000000.00"), Map.entry("calories", -1),
                Map.entry("allergens", List.of("NOT_PUBLISHED")), Map.entry("allergens", List.of("MILK", "MILK")),
                Map.entry("allergens", List.of("UNKNOWN")), Map.entry("allergenStatus", "SAFE"),
                Map.entry("spiceLevel", -1), Map.entry("spiceLevel", 4), Map.entry("spiceLevel", 1.5),
                Map.entry("status", "DELETED"), Map.entry("imageUrl", "javascript:alert(1)"),
                Map.entry("unexpected", true));
        for (var entry : invalid) {
            var body = dishBody(categoryId, "Rice", List.of(), "DECLARED");
            body.put(entry.getKey(), entry.getValue());
            mockMvc.perform(post("/api/admin/dish").header("Authorization", bearer(admin))
                    .contentType(JSON).content(json(body))).andExpect(status().isBadRequest());
        }
        for (String body : List.of("{}", "{\"name\":\" \"}", "{\"name\":\"Test\",\"sort\":-1}",
                "{\"name\":\"Test\",\"status\":\"UNKNOWN\"}", "{\"name\":\"Test\",\"familyId\":\"1\"}")) {
            mockMvc.perform(post("/api/admin/dish-category").header("Authorization", bearer(admin))
                    .contentType(JSON).content(body)).andExpect(status().isBadRequest());
        }
    }

    @Test
    void menusPreserveIdentityAndDeriveOwnershipRejectingInjectedFields() throws Exception {
        var ctx = readyProfile();
        var other = readyProfile();
        String admin = adminToken();
        long categoryId = category(admin);
        long first = dish(admin, categoryId, "Rice", List.of(), "DECLARED");
        long second = dish(admin, categoryId, "Soup", List.of(), "DECLARED");
        long menuId = familyMenu(ctx, List.of(first), today());
        assertEquals(menuId, familyMenu(ctx, List.of(second, first, second), today()));
        assertEquals(List.of(dishRef(second), dishRef(first)), menus.selectById(menuId).getDishIds());
        assertEquals(ctx.familyId().toString(), menus.selectById(menuId).getOwnerKey());
        long otherMenuId = familyMenu(other, List.of(first), today());
        assertNotEquals(menuId, otherMenuId);
        assertCode("E-009", () -> validateOrder(other, menuId, List.of(line(first, 1))));
        var body = menuBody(List.of(first), today());
        for (String field : List.of("familyId", "ownerKey", "sourceType", "childId")) {
            var injected = new LinkedHashMap<>(body);
            injected.put(field, other.familyId().toString());
            mockMvc.perform(post("/api/parent/menu-daily").header("Authorization", bearer(ctx.parentToken()))
                    .contentType(JSON).content(json(injected))).andExpect(status().isBadRequest());
        }
        body.put("school", "Injected school");
        mockMvc.perform(post("/api/parent/menu-daily").header("Authorization", bearer(ctx.parentToken()))
                .contentType(JSON).content(json(body))).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/parent/menu-daily").header("Authorization", bearer(ctx.childToken()))
                .contentType(JSON).content(json(menuBody(List.of(first), today())))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/menu/daily").header("Authorization", bearer(other.parentToken()))
                .param("sourceType", "FAMILY").param("menuDate", today().toString()).param("mealType", "LUNCH")
                .param("childId", childUserId(ctx).toString())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/menu/daily").header("Authorization", bearer(ctx.parentToken()))
                .param("sourceType", "FAMILY").param("menuDate", today().toString()).param("mealType", "LUNCH"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void parentMaintenanceReadsOnlyAvailableCatalogAndCurrentFamilyMenuWithoutChildConsent() throws Exception {
        var ctx = setupFamily();
        var other = setupFamily();
        String admin = adminToken();
        long categoryId = category(admin);
        long available = dish(admin, categoryId, "Available", List.of(), "DECLARED");
        long unavailable = dish(admin, categoryId, "Unavailable", List.of(), "DECLARED");
        long menuId = familyMenu(ctx, List.of(available, unavailable), today());
        Dish offSale = dishes.selectById(unavailable);
        offSale.setStatus("OFF_SALE");
        dishes.updateById(offSale);

        mockMvc.perform(get("/api/parent/dish").header("Authorization", bearer(ctx.parentToken()))
                .param("pageSize", "100").param("categoryId", Long.toString(categoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].dishId").value(Long.toString(available)));
        mockMvc.perform(get("/api/parent/menu-daily").header("Authorization", bearer(ctx.parentToken()))
                .param("menuDate", today().toString()).param("mealType", "LUNCH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.menuId").value(Long.toString(menuId)))
                .andExpect(jsonPath("$.data.dishes[0].dishId").value(Long.toString(available)))
                .andExpect(jsonPath("$.data.dishes[1].dishId").value(Long.toString(unavailable)));

        mockMvc.perform(get("/api/parent/menu-daily").header("Authorization", bearer(other.parentToken()))
                .param("menuDate", today().toString()).param("mealType", "LUNCH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
        for (String token : List.of(ctx.childToken(), other.childToken())) {
            mockMvc.perform(get("/api/parent/dish").header("Authorization", bearer(token)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/parent/menu-daily").header("Authorization", bearer(token))
                    .param("menuDate", today().toString()).param("mealType", "LUNCH"))
                    .andExpect(status().isForbidden());
        }
        String parentWithoutFamily = loginAndSelectRole("catalog-parent-" + UUID.randomUUID(), RoleEnum.PARENT);
        mockMvc.perform(get("/api/parent/dish").header("Authorization", bearer(parentWithoutFamily)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/parent/dish").header("Authorization", bearer(ctx.parentToken()))
                .param("categoryId", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/parent/menu-daily").header("Authorization", bearer(ctx.parentToken()))
                .param("menuDate", today().toString()).param("mealType", "SNACK"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void schoolOnlyDisplaysAndParentPreviewCannotSubmit() throws Exception {
        var ctx = readyProfile();
        String admin = adminToken();
        long dishId = dish(admin, category(admin), "Rice", List.of(), "DECLARED");
        var body = menuBody(List.of(dishId), today());
        body.put("school", profile(ctx).getSchool());
        long schoolMenu = id(mockMvc.perform(post("/api/admin/menu-daily")
                .header("Authorization", bearer(admin)).contentType(JSON).content(json(body)))
                .andExpect(status().isOk()).andReturn(), "menuId");
        assertEquals(schoolMenu, id(mockMvc.perform(post("/api/admin/menu-daily")
                .header("Authorization", bearer(admin)).contentType(JSON).content(json(body)))
                .andExpect(status().isOk()).andReturn(), "menuId"));
        daily(ctx.childToken(), "SCHOOL", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.menuId").value(Long.toString(schoolMenu)))
                .andExpect(jsonPath("$.data.canSubmit").value(false))
                .andExpect(jsonPath("$.data.dishes[0].canSelect").value(false));
        assertCode("E-009", () -> validateOrder(ctx, schoolMenu, List.of(line(dishId, 1))));
        familyMenu(ctx, List.of(dishId), today());
        daily(ctx.parentToken(), "FAMILY", childUserId(ctx)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canSubmit").value(false));
        mockMvc.perform(post("/api/admin/menu-daily").header("Authorization", bearer(ctx.parentToken()))
                .contentType(JSON).content(json(body))).andExpect(status().isForbidden());
    }

    @Test
    void schoolNameMustComeFromPublishedDirectoryOnBothSides() throws Exception {
        var ctx = readyProfile();
        String admin = adminToken();
        long dishId = dish(admin, category(admin), "Rice", List.of(), "DECLARED");
        // 录入侧：校名不在发布目录 ⇒ 拒绝。校名是 SCHOOL 菜单的归属键文本，手输错字会让该校孩子看不到菜单。
        var body = menuBody(List.of(dishId), today());
        body.put("school", "未登记小学");
        mockMvc.perform(post("/api/admin/menu-daily").header("Authorization", bearer(admin))
                .contentType(JSON).content(json(body))).andExpect(status().isBadRequest());
        // 目录内校名可发布，且与家长档案同值时孩子能取到（展示闭环），但依旧不可下单。
        body.put("school", profile(ctx).getSchool());
        long menuId = id(mockMvc.perform(post("/api/admin/menu-daily").header("Authorization", bearer(admin))
                .contentType(JSON).content(json(body))).andExpect(status().isOk()).andReturn(), "menuId");
        daily(ctx.childToken(), "SCHOOL", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.menuId").value(Long.toString(menuId)))
                .andExpect(jsonPath("$.data.canSubmit").value(false));
        // 档案侧：校名不在发布目录 ⇒ 拒绝（与年级、过敏原同一白名单机制）。
        String rejected = profileJson(ctx).replace("\"school\":\"合成学校\"", "\"school\":\"未登记小学\"");
        mockMvc.perform(post("/api/child/profile").header("Authorization", bearer(ctx.parentToken()))
                .contentType(JSON).content(rejected)).andExpect(status().isBadRequest());
        // 已在目录内的校名不受影响。
        mockMvc.perform(post("/api/child/profile").header("Authorization", bearer(ctx.parentToken()))
                .contentType(JSON).content(profileJson(ctx))).andExpect(status().isOk());
    }

    @Test
    void adminListsDishesPendingAllergenDeclaration() throws Exception {
        String admin = adminToken();
        long categoryId = category(admin);
        long declared = dish(admin, categoryId, "DeclaredOnly", List.of("MILK"), "DECLARED");
        // 未声明的菜只能存在于下架态（R5-c），仍必须能被管理员筛出来补全。
        long pending = dish(admin, categoryId, "PendingOnly", List.of(), "UNKNOWN", "OFF_SALE");
        mockMvc.perform(get("/api/admin/dish").param("allergenStatus", "UNKNOWN").param("keyword", "PendingOnly")
                .header("Authorization", bearer(admin))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].dishId").value(Long.toString(pending)))
                .andExpect(jsonPath("$.data.items[0].allergenStatus").value("UNKNOWN"));
        mockMvc.perform(get("/api/admin/dish").param("allergenStatus", "DECLARED").param("keyword", "PendingOnly")
                .header("Authorization", bearer(admin))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
        mockMvc.perform(get("/api/admin/dish").param("allergenStatus", "DECLARED").param("keyword", "DeclaredOnly")
                .header("Authorization", bearer(admin))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].dishId").value(Long.toString(declared)));
        mockMvc.perform(get("/api/admin/dish").param("allergenStatus", "SAFE")
                .header("Authorization", bearer(admin))).andExpect(status().isBadRequest());
    }

    @Test
    void onSaleRequiresDeclaredAllergen() throws Exception {
        String admin = adminToken();
        long categoryId = category(admin);
        // 直接上架未声明的菜 ⇒ 拒绝（否则孩子端静默点不了：safetyStatus=UNKNOWN、canSelect=false）。
        mockMvc.perform(post("/api/admin/dish").header("Authorization", bearer(admin))
                .contentType(JSON).content(json(dishBody(categoryId, "Draft", List.of(), "UNKNOWN", "ON_SALE"))))
                .andExpect(status().isBadRequest());
        long draft = dish(admin, categoryId, "Draft", List.of(), "UNKNOWN", "OFF_SALE");
        // 未声明菜品不允许改为上架
        mockMvc.perform(put("/api/admin/dish/{id}", draft).header("Authorization", bearer(admin))
                .contentType(JSON).content(json(dishBody(categoryId, "Draft", List.of(), "UNKNOWN", "ON_SALE"))))
                .andExpect(status().isBadRequest());
        assertEquals("OFF_SALE", dishes.selectById(draft).getStatus());
        // 补上声明后即可上架
        mockMvc.perform(put("/api/admin/dish/{id}", draft).header("Authorization", bearer(admin))
                .contentType(JSON).content(json(dishBody(categoryId, "Draft", List.of(), "DECLARED", "ON_SALE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allergenStatus").value("DECLARED"));
        assertEquals("ON_SALE", dishes.selectById(draft).getStatus());
    }

    @Test
    void displayExplainsUnknownAllergyDislikeAndMissingDishes() throws Exception {
        var ctx = readyProfile();
        String admin = adminToken();
        long categoryId = category(admin);
        long safe = dish(admin, categoryId, "Carrot stew", List.of(), "DECLARED");
        long unknown = unknownDish(admin, categoryId, "Unknown");
        long allergic = dish(admin, categoryId, "Peanut dish", List.of("PEANUT"), "DECLARED");
        long removed = dish(admin, categoryId, "Removed", List.of(), "DECLARED");
        long offSale = dish(admin, categoryId, "Unavailable", List.of(), "DECLARED");
        long menuId = familyMenu(ctx, List.of(safe, unknown, allergic, removed, offSale), today());
        mockMvc.perform(put("/api/child/preferences").header("Authorization", bearer(ctx.childToken()))
                .contentType(JSON).content("{\"dislikes\":[\"Carrot\"],\"tastes\":[]}")).andExpect(status().isOk());
        favorite(ctx.childToken(), safe, true, menuId).andExpect(status().isOk());
        mockMvc.perform(delete("/api/admin/dish/{id}", removed).header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
        Dish unavailable = dishes.selectById(offSale);
        unavailable.setStatus("OFF_SALE");
        dishes.updateById(unavailable);
        daily(ctx.childToken(), "FAMILY", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canSubmit").value(true))
                .andExpect(jsonPath("$.data.dishes[0].dishId").value(Long.toString(safe)))
                .andExpect(jsonPath("$.data.dishes[0].virtualPrice").value("18.00"))
                .andExpect(jsonPath("$.data.dishes[0].isDisliked").value(true))
                .andExpect(jsonPath("$.data.dishes[0].isFavorite").value(true))
                .andExpect(jsonPath("$.data.dishes[0].canSelect").value(true))
                .andExpect(jsonPath("$.data.dishes[1].safetyStatus").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.dishes[1].canSelect").value(false))
                .andExpect(jsonPath("$.data.dishes[2].allergyConflict").value(true))
                .andExpect(jsonPath("$.data.dishes[2].safetyStatus").value("ALLERGY_CONFLICT"))
                .andExpect(jsonPath("$.data.dishes[3].safetyStatus").value("OFF_SALE"))
                .andExpect(jsonPath("$.data.missingDishIds[0]").value(Long.toString(removed)));
        assertNotNull(validateOrder(ctx, menus.selectOne(new QueryWrapper<MenuDaily>()
                .eq("family_id", ctx.familyId())).getId(), List.of(line(safe, 1))));
    }

    @Test
    void orderValidationPreservesRequestedOrderAndRejectsUnsafeAndStaleCatalog() throws Exception {
        var ctx = readyProfile();
        String admin = adminToken();
        long categoryId = category(admin);
        long first = dish(admin, categoryId, "First", List.of(), "DECLARED");
        long second = dish(admin, categoryId, "Second", List.of("MILK"), "DECLARED");
        long unknown = unknownDish(admin, categoryId, "Unknown");
        long allergic = dish(admin, categoryId, "Peanut", List.of("PEANUT"), "DECLARED");
        long outside = dish(admin, categoryId, "Outside", List.of(), "DECLARED");
        long menuId = familyMenu(ctx, List.of(first, second, unknown, allergic), today());
        var validated = validateOrder(ctx, menuId, List.of(line(second, 9), line(first, 1)));
        assertEquals(menuId, validated.menu().getId());
        assertEquals(List.of(second, first), validated.dishes().stream().map(Dish::getId).toList());
        var parentValidated = asUser(ctx.parentToken(), () -> catalog.validateOrder(menuId,
                authorization.lockBoundChild(childUserId(ctx)), List.of(line(second, 1))));
        assertEquals(second, parentValidated.dishes().getFirst().getId());
        for (long unsafe : List.of(unknown, allergic)) {
            assertCode("E-007", () -> validateOrder(ctx, menuId, List.of(line(unsafe, 1))));
        }
        assertCode("E-400", () -> validateOrder(ctx, menuId, List.of(line(outside, 1))));
        Dish downlisted = dishes.selectById(first);
        downlisted.setStatus("OFF_SALE");
        dishes.updateById(downlisted);
        assertCode("E-007", () -> validateOrder(ctx, menuId, List.of(line(first, 1))));
        MenuDaily menu = menus.selectById(menuId);
        menu.setStatus("DRAFT");
        menus.updateById(menu);
        assertCode("E-007", () -> validateOrder(ctx, menuId, List.of(line(second, 1))));
        daily(ctx.childToken(), "FAMILY", null).andExpect(status().isNotFound());
        menu.setStatus("PUBLISHED");
        menus.updateById(menu);
        LocalDate date = today();
        try {
            doReturn(date.plusDays(1)).when(time).today();
            assertCode("E-007", () -> validateOrder(ctx, menuId, List.of(line(second, 1))));
        } finally {
            reset(time);
        }
        dishes.deleteById(second);
        assertCode("E-404", () -> validateOrder(ctx, menuId, List.of(line(second, 1))));
    }

    @Test
    void orderLineBoundsAndMenuBoundsFailBeforeWrites() throws Exception {
        var ctx = readyProfile();
        String admin = adminToken();
        long dishId = dish(admin, category(admin), "Rice", List.of(), "DECLARED");
        long menuId = familyMenu(ctx, List.of(dishId), today());
        assertCode("E-400", () -> validateOrder(ctx, menuId, null));
        assertCode("E-400", () -> validateOrder(ctx, menuId, List.of()));
        assertCode("E-400", () -> validateOrder(ctx, menuId, Arrays.asList((OrderLineReq) null)));
        assertCode("E-400", () -> validateOrder(ctx, menuId, Collections.nCopies(21, line(dishId, 1))));
        assertCode("E-400", () -> validateOrder(ctx, menuId, List.of(line(dishId, 1), line(dishId, 2))));
        for (Integer quantity : Arrays.asList(null, 0, 10, -1)) {
            assertCode("E-400", () -> validateOrder(ctx, menuId, List.of(line(dishId, quantity))));
        }
        for (Long invalidId : Arrays.asList(null, 0L, -1L)) {
            assertCode("E-400", () -> validateOrder(ctx, menuId, List.of(line(invalidId, 1))));
        }
        OrderLineReq longNote = line(dishId, 1);
        longNote.setNote("x".repeat(256));
        assertCode("E-400", () -> validateOrder(ctx, menuId, List.of(longNote)));
        longNote.setNote("x".repeat(255));
        assertNotNull(validateOrder(ctx, menuId, List.of(longNote)));
        for (var ids : List.of(List.of(), List.of(0L), Collections.nCopies(51, dishId))) {
            var body = menuBody(List.of(dishId), today());
            body.put("dishIds", ids);
            mockMvc.perform(post("/api/parent/menu-daily").header("Authorization", bearer(ctx.parentToken()))
                    .contentType(JSON).content(json(body))).andExpect(status().isBadRequest());
        }
        for (String meal : List.of("", "SNACK", "lunch")) {
            var body = menuBody(List.of(dishId), today());
            body.put("mealType", meal);
            mockMvc.perform(post("/api/parent/menu-daily").header("Authorization", bearer(ctx.parentToken()))
                    .contentType(JSON).content(json(body))).andExpect(status().isBadRequest());
        }
        long before = menus.selectCount(new QueryWrapper<MenuDaily>().eq("family_id", ctx.familyId()));
        mockMvc.perform(post("/api/parent/menu-daily").header("Authorization", bearer(ctx.parentToken()))
                .contentType(JSON).content(json(menuBody(List.of(Long.MAX_VALUE), today().plusDays(1)))))
                .andExpect(status().isNotFound());
        assertEquals(before, menus.selectCount(new QueryWrapper<MenuDaily>().eq("family_id", ctx.familyId())));
    }

    @Test
    void dailyWantEatIsScopedToChildAndMealAndExposesParentView() throws Exception {
        var ctx = readyProfile();
        var other = readyProfile();
        String admin = adminToken();
        long dishId = unknownDish(admin, category(admin), "Want-eatable");
        long menuId = familyMenu(ctx, List.of(dishId), today());
        // 幂等：标记两次仍只有一行
        for (int attempt = 0; attempt < 2; attempt++) {
            favorite(ctx.childToken(), dishId, true, menuId).andExpect(status().isOk());
        }
        assertEquals(1L, wantEats.selectCount(new QueryWrapper<ChildWantEat>().eq("child_id", childUserId(ctx))
                .eq("menu_date", today()).eq("meal_type", "LUNCH").eq("dish_type", "PRESET").eq("dish_id", dishId)));
        // 家长不能标记（CHILD 专属）
        favorite(ctx.parentToken(), dishId, true, menuId).andExpect(status().isForbidden());
        // 他人 childId 被拒（未知字段）
        mockMvc.perform(post("/api/menu/mark-favorite").header("Authorization", bearer(other.childToken()))
                .contentType(JSON).content(json(Map.of("dishId", dishId, "favorite", true,
                        "menuId", menuId, "menuDate", today().toString(), "mealType", "LUNCH",
                        "childId", childUserId(ctx))))).andExpect(status().isBadRequest());
        assertEquals(0L, wantEats.selectCount(new QueryWrapper<ChildWantEat>().eq("child_id", childUserId(other))));
        // 取消 → 行消失
        favorite(ctx.childToken(), dishId, false, menuId).andExpect(status().isOk());
        assertEquals(0L, wantEats.selectCount(new QueryWrapper<ChildWantEat>().eq("child_id", childUserId(ctx))
                .eq("dish_id", dishId)));
        // 重新标记后，家长可查询到孩子的每日想吃清单
        favorite(ctx.childToken(), dishId, true, menuId).andExpect(status().isOk());
        childWantEat(ctx.parentToken(), childUserId(ctx), today(), "LUNCH").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.menuDate").value(today().toString()))
                .andExpect(jsonPath("$.data.mealType").value("LUNCH"))
                .andExpect(jsonPath("$.data.wantEat.length()").value(1))
                .andExpect(jsonPath("$.data.wantEat[0].type").value("PRESET"))
                .andExpect(jsonPath("$.data.wantEat[0].id").value(Long.toString(dishId)));
        childWantEat(ctx.childToken(), null, today(), "LUNCH").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.wantEat.length()").value(1));
        // 缺字段 → 400
        for (String body : List.of("{}", "{\"dishId\":1}", "{\"dishId\":1,\"favorite\":true}",
                "{\"dishId\":1,\"favorite\":null,\"menuId\":1,\"menuDate\":\"" + today() + "\",\"mealType\":\"LUNCH\"}",
                "{\"dishId\":1.5,\"favorite\":true,\"menuId\":1,\"menuDate\":\"" + today() + "\",\"mealType\":\"LUNCH\"}")) {
            mockMvc.perform(post("/api/menu/mark-favorite").header("Authorization", bearer(ctx.childToken()))
                    .contentType(JSON).content(body)).andExpect(status().isBadRequest());
        }
        // 缺 dishType → 400
        mockMvc.perform(post("/api/menu/mark-favorite").header("Authorization", bearer(ctx.childToken()))
                .contentType(JSON).content(json(Map.of("dishId", dishId, "favorite", true,
                        "menuId", menuId, "menuDate", today().toString(), "mealType", "LUNCH"))))
                .andExpect(status().isBadRequest());
        // 菜品下架后标记 → 404
        mockMvc.perform(delete("/api/admin/dish/{id}", dishId).header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
        favorite(ctx.childToken(), dishId, true, menuId).andExpect(status().isNotFound());
    }

    private ResultActions childWantEat(String token, Long childId, LocalDate menuDate, String mealType)
            throws Exception {
        var request = get("/api/child/want-eat").header("Authorization", bearer(token))
                .param("menuDate", menuDate.toString()).param("mealType", mealType);
        if (childId != null) {
            request.param("childId", childId.toString());
        }
        return mockMvc.perform(request);
    }

    @Test
    void wantEatIsIsolatedByDateAndMeal() throws Exception {
        var ctx = readyProfile();
        String admin = adminToken();
        long categoryId = category(admin);
        long dishId = dish(admin, categoryId, "Daily scoped", List.of(), "DECLARED");
        long menuId = familyMenu(ctx, List.of(dishId), today());
        // A 日午餐标记想吃
        favorite(ctx.childToken(), dishId, true, menuId).andExpect(status().isOk());
        daily(ctx.childToken(), "FAMILY", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dishes[0].isFavorite").value(true));
        // 为验证「按餐次隔离」，另建一份同菜的 DINNER 菜单
        mockMvc.perform(post("/api/parent/menu-daily").header("Authorization", bearer(ctx.parentToken()))
                .contentType(JSON).content(json(Map.of("menuDate", today().toString(), "mealType", "DINNER",
                        "dishIds", List.of(Map.of("type", "PRESET", "id", dishId))))))
                .andExpect(status().isOk());
        // A 日晚餐不高亮
        daily(ctx.childToken(), "FAMILY", null, "DINNER", today()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dishes[0].isFavorite").value(false));
        // B 日午餐不高亮
        LocalDate tomorrow = today().plusDays(1);
        familyMenu(ctx, List.of(dishId), tomorrow);
        daily(ctx.childToken(), "FAMILY", null, "LUNCH", tomorrow).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dishes[0].isFavorite").value(false));
        // 取消 A 日午餐后不高亮
        favorite(ctx.childToken(), dishId, false, menuId).andExpect(status().isOk());
        daily(ctx.childToken(), "FAMILY", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dishes[0].isFavorite").value(false));
        assertEquals(0L, wantEats.selectCount(new QueryWrapper<ChildWantEat>().eq("child_id", childUserId(ctx))));
    }

    @Test
    void wantEatQueryIsScopedToFamilyForParent() throws Exception {
        var ctx = readyProfile();
        var other = readyProfile();
        String admin = adminToken();
        long dishId = dish(admin, category(admin), "Parent view", List.of(), "DECLARED");
        long menuId = familyMenu(ctx, List.of(dishId), today());
        favorite(ctx.childToken(), dishId, true, menuId).andExpect(status().isOk());
        // 同家庭家长可见
        childWantEat(ctx.parentToken(), childUserId(ctx), today(), "LUNCH").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.wantEat.length()").value(1));
        // 别家家长 / 别家孩子均越权
        childWantEat(other.parentToken(), childUserId(ctx), today(), "LUNCH").andExpect(status().isForbidden());
        childWantEat(other.childToken(), childUserId(ctx), today(), "LUNCH").andExpect(status().isForbidden());
    }

    @Test
    void presetAndFamilyDishWithSameIdDoNotCollide() throws Exception {
        var ctx = readyProfile();
        String admin = adminToken();
        long categoryId = category(admin);
        long presetId = dish(admin, categoryId, "Preset dish", List.of(), "DECLARED");
        long familyId = familyDish(ctx, categoryId, "Family dish", "DECLARED");
        Map<String, Object> presetRef = new HashMap<>();
        presetRef.put("type", "PRESET");
        presetRef.put("id", presetId);
        Map<String, Object> familyRef = new HashMap<>();
        familyRef.put("type", "FAMILY");
        familyRef.put("id", familyId);
        var refs = List.of(presetRef, familyRef);
        long menuId = familyMenuWithRefs(ctx, refs, today());
        favorite(ctx.childToken(), presetId, true, menuId).andExpect(status().isOk());
        favorite(ctx.childToken(), familyId, true, menuId, "FAMILY").andExpect(status().isOk());
        assertEquals(1L, wantEats.selectCount(new QueryWrapper<ChildWantEat>()
                .eq("child_id", childUserId(ctx)).eq("dish_type", "PRESET").eq("dish_id", presetId)));
        assertEquals(1L, wantEats.selectCount(new QueryWrapper<ChildWantEat>()
                .eq("child_id", childUserId(ctx)).eq("dish_type", "FAMILY").eq("dish_id", familyId)));
        // 菜单菜品顺序固定（PRESET 在前、FAMILY 在后），按索引断言两道菜均被正确高亮。
        daily(ctx.childToken(), "FAMILY", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dishes[0].sourceType").value("PRESET"))
                .andExpect(jsonPath("$.data.dishes[0].isFavorite").value(true))
                .andExpect(jsonPath("$.data.dishes[1].sourceType").value("FAMILY"))
                .andExpect(jsonPath("$.data.dishes[1].isFavorite").value(true));
    }

    @Test
    void markingAgainstNonExistentMenuFails() throws Exception {
        var ctx = readyProfile();
        String admin = adminToken();
        long dishId = dish(admin, category(admin), "Menu bound", List.of(), "DECLARED");
        favorite(ctx.childToken(), dishId, true, 9_999_999L).andExpect(status().isNotFound());
    }

    private long familyDish(FamilyContext ctx, long categoryId, String name, String allergenStatus)
            throws Exception {
        return id(mockMvc.perform(post("/api/parent/family-dish").header("Authorization", bearer(ctx.parentToken()))
                .contentType(JSON).content(json(Map.of("categoryId", Long.toString(categoryId), "name", name,
                        "virtualPrice", "12.00", "allergens", List.of(), "allergenStatus", allergenStatus,
                        "spiceLevel", 0)))).andExpect(status().isOk()).andReturn(), "dishId");
    }

    @Test
    void childDataRequiresBindingCompleteProfileAndCurrentConsent() throws Exception {
        var ctx = setupFamily();
        String admin = adminToken();
        long dishId = dish(admin, category(admin), "Rice", List.of(), "DECLARED");
        long menuId = familyMenu(ctx, List.of(dishId), today());
        favorite(ctx.childToken(), dishId, true, menuId).andExpect(status().isForbidden());
        grant(ctx);
        approve(ctx);
        favorite(ctx.childToken(), dishId, true, menuId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("E-002"));
        daily(ctx.childToken(), "FAMILY", null).andExpect(status().isConflict());
        saveProfile(ctx);
        favorite(ctx.childToken(), dishId, true, menuId).andExpect(status().isOk());
        revoke(ctx);
        favorite(ctx.childToken(), dishId, false, menuId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("E-010"));
        daily(ctx.childToken(), "FAMILY", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("E-010"));
        mockMvc.perform(get("/api/child/preferences").header("Authorization", bearer(ctx.parentToken()))
                .param("childId", childUserId(ctx).toString())).andExpect(status().isConflict());
        grant(ctx);
        favorite(ctx.childToken(), dishId, false, menuId).andExpect(status().isOk());
    }

    @Test
    void unpublishedAllergenCatalogFailsClosed() throws Exception {
        var ctx = readyProfile();
        String admin = adminToken();
        long categoryId = category(admin);
        long dishId = dish(admin, categoryId, "Rice", List.of(), "DECLARED");
        long menuId = familyMenu(ctx, List.of(dishId), today());
        String reference = policy.getCatalogReference();
        try {
            policy.setCatalogReference("");
            mockMvc.perform(post("/api/admin/dish").header("Authorization", bearer(admin))
                    .contentType(JSON).content(json(dishBody(categoryId, "Rice", List.of(), "DECLARED"))))
                    .andExpect(status().isBadRequest());
            daily(ctx.childToken(), "FAMILY", null).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.canSubmit").value(false))
                    .andExpect(jsonPath("$.data.dishes[0].safetyStatus").value("UNKNOWN"));
            assertCode("E-007", () -> validateOrder(ctx, menuId, List.of(line(dishId, 1))));
        } finally {
            policy.setCatalogReference(reference);
        }
    }

    @Test
    void concurrentFirstPublicationKeepsSingleMenuAndFavoritesDoNotLoseUpdates() throws Exception {
        var ctx = readyProfile();
        String admin = adminToken();
        long categoryId = category(admin);
        long first = dish(admin, categoryId, "First", List.of(), "DECLARED");
        long second = dish(admin, categoryId, "Second", List.of(), "DECLARED");
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(4)) {
            List<Future<Long>> publications = new ArrayList<>();
            for (int index = 0; index < 4; index++) {
                publications.add(pool.submit(() -> {
                    await(start);
                    return familyMenu(ctx, List.of(second, first), today());
                }));
            }
            start.countDown();
            Set<Long> ids = new HashSet<>();
            for (Future<Long> publication : publications) {
                ids.add(publication.get(20, TimeUnit.SECONDS));
            }
            assertEquals(1, ids.size());
            long menuId = ids.iterator().next();
            Future<?> one = pool.submit(() -> favorite(ctx.childToken(), first, true, menuId).andExpect(status().isOk()));
            Future<?> two = pool.submit(() -> favorite(ctx.childToken(), second, true, menuId).andExpect(status().isOk()));
            one.get(20, TimeUnit.SECONDS);
            two.get(20, TimeUnit.SECONDS);
            assertEquals(1L, wantEats.selectCount(new QueryWrapper<ChildWantEat>().eq("child_id", childUserId(ctx))
                    .eq("menu_date", today()).eq("meal_type", "LUNCH").eq("dish_type", "PRESET").eq("dish_id", first)));
            assertEquals(1L, wantEats.selectCount(new QueryWrapper<ChildWantEat>().eq("child_id", childUserId(ctx))
                    .eq("menu_date", today()).eq("meal_type", "LUNCH").eq("dish_type", "PRESET").eq("dish_id", second)));
        }
    }

    @Test
    void validationHoldsDishLocksUntilCallerTransactionCompletes() throws Exception {
        var ctx = readyProfile();
        String admin = adminToken();
        long dishId = dish(admin, category(admin), "Rice", List.of(), "DECLARED");
        long menuId = familyMenu(ctx, List.of(dishId), today());
        CountDownLatch validated = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch updating = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<?> reader = pool.submit(() -> asUser(ctx.childToken(), () -> {
                var member = authorization.lockBoundChild(childUserId(ctx));
                catalog.validateOrder(menuId, member, List.of(line(dishId, 1)));
                validated.countDown();
                await(release);
                return null;
            }));
            await(validated);
            Future<?> writer = pool.submit(() -> {
                updating.countDown();
                return mockMvc.perform(delete("/api/admin/dish/{id}", dishId)
                        .header("Authorization", bearer(admin))).andExpect(status().isOk());
            });
            try {
                await(updating);
                assertThrows(TimeoutException.class, () -> writer.get(250, TimeUnit.MILLISECONDS));
            } finally {
                release.countDown();
            }
            reader.get(20, TimeUnit.SECONDS);
            writer.get(20, TimeUnit.SECONDS);
            assertCode("E-404", () -> validateOrder(ctx, menuId, List.of(line(dishId, 1))));
        } finally {
            release.countDown();
        }
    }

    @Test
    void validationObservesDownlistingCommittedBeforeItAcquiresDishLock() throws Exception {
        var ctx = readyProfile();
        String admin = adminToken();
        long dishId = dish(admin, category(admin), "Rice", List.of(), "DECLARED");
        long menuId = familyMenu(ctx, List.of(dishId), today());
        CountDownLatch changed = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch reading = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<?> writer = pool.submit(() -> asUser(admin, () -> {
                Dish dish = dishes.selectOne(new QueryWrapper<Dish>().eq("id", dishId).last("FOR UPDATE"));
                dish.setStatus("OFF_SALE");
                dishes.updateById(dish);
                changed.countDown();
                await(release);
                return null;
            }));
            await(changed);
            Future<?> reader = pool.submit(() -> {
                reading.countDown();
                assertCode("E-007", () -> validateOrder(ctx, menuId, List.of(line(dishId, 1))));
            });
            try {
                await(reading);
                assertThrows(TimeoutException.class, () -> reader.get(250, TimeUnit.MILLISECONDS));
            } finally {
                release.countDown();
            }
            writer.get(20, TimeUnit.SECONDS);
            reader.get(20, TimeUnit.SECONDS);
        } finally {
            release.countDown();
        }
    }

    private String adminToken() {
        User admin = new User();
        admin.setOpenid("catalog-admin-" + UUID.randomUUID());
        admin.setRole("ADMIN");
        admin.setStatus("NORMAL");
        users.insert(admin);
        return tokenWithJti(admin.getId(), RoleEnum.ADMIN, List.of(), UUID.randomUUID().toString());
    }

    private long category(String admin) throws Exception {
        return id(mockMvc.perform(post("/api/admin/dish-category").header("Authorization", bearer(admin))
                .contentType(JSON).content("{\"name\":\"Synthetic category\",\"sort\":0,\"status\":\"ENABLED\"}"))
                .andExpect(status().isOk()).andReturn(), "categoryId");
    }

    private long dish(String admin, long categoryId, String name, List<String> allergens, String allergenStatus)
            throws Exception {
        return dish(admin, categoryId, name, allergens, allergenStatus, "ON_SALE");
    }

    private long dish(String admin, long categoryId, String name, List<String> allergens, String allergenStatus,
            String status) throws Exception {
        return id(mockMvc.perform(post("/api/admin/dish").header("Authorization", bearer(admin))
                .contentType(JSON).content(json(dishBody(categoryId, name, allergens, allergenStatus, status))))
                .andExpect(status().isOk()).andReturn(), "dishId");
    }

    private Map<String, Object> dishBody(long categoryId, String name, List<String> allergens, String allergenStatus) {
        return dishBody(categoryId, name, allergens, allergenStatus, "ON_SALE");
    }

    private Map<String, Object> dishBody(long categoryId, String name, List<String> allergens, String allergenStatus,
            String status) {
        return new LinkedHashMap<>(Map.of("categoryId", Long.toString(categoryId), "name", name,
                "virtualPrice", "18.00", "allergens", allergens, "allergenStatus", allergenStatus,
                "spiceLevel", 0, "status", status));
    }

    /**
     * 构造「已上架但过敏信息未知」的菜品。R5-c 生效后该组合无法经 API 创建（ON_SALE 必须先 DECLARED），
     * 故先以 OFF_SALE 合法创建，再直接改库模拟「历史行 / 发布目录漂移」，
     * 以保留孩子端 UNKNOWN 展示路径（safetyStatus=UNKNOWN、canSelect=false）的覆盖。
     */
    private long unknownDish(String admin, long categoryId, String name) throws Exception {
        long id = dish(admin, categoryId, name, List.of(), "UNKNOWN", "OFF_SALE");
        Dish row = dishes.selectById(id);
        row.setStatus("ON_SALE");
        dishes.updateById(row);
        return id;
    }

    private Map<String, Object> menuBody(List<Long> dishIds, LocalDate date) {
        var refs = dishIds.stream().map(this::refBody).toList();
        return new LinkedHashMap<>(Map.of("menuDate", date.toString(), "mealType", "LUNCH", "dishIds", refs));
    }

    private Map<String, Object> refBody(long id) {
        return Map.of("type", "PRESET", "id", id);
    }

    private DishRef dishRef(Long id) {
        DishRef dishRef = new DishRef();
        dishRef.setType("PRESET");
        dishRef.setId(id);
        return dishRef;
    }

    private long familyMenu(FamilyContext ctx, List<Long> dishIds, LocalDate date) throws Exception {
        return id(mockMvc.perform(post("/api/parent/menu-daily").header("Authorization", bearer(ctx.parentToken()))
                .contentType(JSON).content(json(menuBody(dishIds, date))))
                .andExpect(status().isOk()).andReturn(), "menuId");
    }

    private long familyMenuWithRefs(FamilyContext ctx, List<Map<String, Object>> refs, LocalDate date) throws Exception {
        var body = Map.of("menuDate", date.toString(), "mealType", "LUNCH", "dishIds", refs);
        return id(mockMvc.perform(post("/api/parent/menu-daily").header("Authorization", bearer(ctx.parentToken()))
                .contentType(JSON).content(json(body))).andExpect(status().isOk()).andReturn(), "menuId");
    }

    private ResultActions daily(String token, String source, Long childId) throws Exception {
        return daily(token, source, childId, "LUNCH", today());
    }

    private ResultActions daily(String token, String source, Long childId, String mealType, LocalDate date)
            throws Exception {
        var request = get("/api/menu/daily").header("Authorization", bearer(token))
                .param("sourceType", source).param("menuDate", date.toString()).param("mealType", mealType);
        if (childId != null) {
            request.param("childId", childId.toString());
        }
        return mockMvc.perform(request);
    }

    private ResultActions favorite(String token, long dishId, boolean favorite, long menuId) throws Exception {
        return favorite(token, dishId, favorite, menuId, "PRESET");
    }

    private ResultActions favorite(String token, long dishId, boolean favorite, long menuId, String dishType)
            throws Exception {
        return mockMvc.perform(post("/api/menu/mark-favorite").header("Authorization", bearer(token))
                .contentType(JSON).content(json(Map.of("dishId", Long.toString(dishId), "favorite", favorite,
                        "menuId", Long.toString(menuId), "menuDate", today().toString(), "mealType", "LUNCH",
                        "dishType", dishType))));
    }

    private FamilyContext readyProfile() throws Exception {
        var ctx = setupFamily();
        grant(ctx);
        approve(ctx);
        saveProfile(ctx);
        return ctx;
    }

    private void saveProfile(FamilyContext ctx) throws Exception {
        mockMvc.perform(post("/api/child/profile").header("Authorization", bearer(ctx.parentToken()))
                .contentType(JSON).content(profileJson(ctx))).andExpect(status().isOk());
    }

    private ChildProfile profile(FamilyContext ctx) {
        return profiles.selectOne(new QueryWrapper<ChildProfile>().eq("user_id", childUserId(ctx)));
    }

    private OrderLineReq line(Long dishId, Integer quantity) {
        OrderLineReq line = new OrderLineReq();
        line.setDishRef(dishRef(dishId));
        line.setQuantity(quantity);
        return line;
    }

    private CatalogService.ValidatedMenu validateOrder(FamilyContext ctx, long menuId, List<OrderLineReq> lines) {
        return asUser(ctx.childToken(), () -> catalog.validateOrder(menuId,
                authorization.lockBoundChild(childUserId(ctx)), lines));
    }

    private <T> T asUser(String token, Supplier<T> action) {
        UserContext.set(sessions.authenticate(token));
        try {
            return new TransactionTemplate(transactions).execute(transaction -> action.get());
        } finally {
            UserContext.clear();
        }
    }

    private void assertCode(String code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(BizException.class, action).getResultCode().getCode());
    }

    private void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS), "Timed out waiting for transaction synchronization");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError(ex);
        }
    }

    private long id(MvcResult result, String field) throws Exception {
        JsonNode value = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get(field);
        assertTrue(value.isString(), "Business identifiers must be JSON strings");
        return Long.parseLong(value.asString());
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
}

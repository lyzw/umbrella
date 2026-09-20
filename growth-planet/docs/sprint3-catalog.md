# Sprint 3 Catalog and Favorites

Implementation and integration: 2026-09-18. The final full suite passed 100 tests,
including 14 catalog cases, at 13:36:24. See the
[verification record](sprint2-4-verification.md) for commands and limits.
This is isolated synthetic-data validation, not production acceptance.

## Integration Contract

```java
@Transactional
public CatalogService.ValidatedMenu validateOrder(
        Long menuId, FamilyMember member, List<OrderLineReq> items);

public record ValidatedMenu(MenuDaily menu, List<Dish> dishes) {}
```

`ValidatedMenu` is a public nested record in `CatalogService`. Returned dishes
follow the caller's item order, not primary-key order. `OrderLineReq` has Lombok
getters/setters for `Long dishId`, `Integer quantity`, and nullable `String note`.
`Dish` exposes `getId()`, `getName()`, and `BigDecimal getVirtualPrice()`;
`MenuDaily` exposes `LocalDate getMenuDate()` and `getMealType()`.

The caller must hold current family/child/relation authorization locks and any
confirmation lock in the same outer transaction before calling validation. The
service checks live consent and COMPLETE profile, locks the menu FOR UPDATE,
then locks distinct dish primary keys individually in ascending order FOR UPDATE.
Locks remain held by the caller transaction. Do not call it before starting the
transaction that persists the confirmation/approval.

Validation requires FAMILY ownership, PUBLISHED status, `BusinessTime.today()`,
1-20 distinct lines, quantity 1-9, note at most 255 characters, menu membership,
ON_SALE dishes, DECLARED allergens in the published configured catalog, and no
profile allergy intersection. Null/obsolete allergy codes fail closed. Empty
DECLARED arrays are allowed but are not a claim of guaranteed allergen absence.
Dislikes are display-only. UNKNOWN is not an allergen code; it is a declaration
status and blocks ordering even when the stored allergen array is empty.

All date checks use the shared `BusinessTime` bean (Asia/Shanghai), including
tests that move the date. Catalog does not set prices or modify confirmation
snapshots during approval.

The overload `validateOrder(menuId, member, items, LocalDate usageDate)` accepts
the settlement date captured by the confirmation service. Menu validation,
allowance periods, previews and ledger rows share that date; the debit path
rejects a date rollover before changing the balance.

FAMILY upsert locks the current family, verifies bound parent membership, and
derives `owner_key` from its ID. SCHOOL upsert derives the key from trimmed school
text. A unique-key insert-or-keep followed by a locking read updates the same
menu ID, including concurrent first publication. Dishes are checked under locks
before publication commits. DRAFT/PUBLISHED both preserve the same identity.
Existing unique keys cannot be reused after logical deletion.

## HTTP Interfaces

All endpoints use the existing `{code,data,message,requestId}` envelope.
Business IDs and ID arrays are decimal strings in responses. `virtualPrice` is
an explicit String DTO field, formatted with two decimal places.

| Method and path | Role and payload |
| --- | --- |
| POST `/api/admin/dish-category` | ADMIN; name (1-32), sort (nonnegative, default 0), status (ENABLED/DISABLED, default ENABLED) |
| GET `/api/admin/dish-category` | ADMIN; page/pageSize; sorted by sort then ID |
| POST `/api/admin/dish` | ADMIN; complete dish body below |
| GET `/api/admin/dish` | ADMIN; page/pageSize, optional positive categoryId, status ON_SALE/OFF_SALE, keyword (1-64) |
| GET `/api/admin/dish/{id}` | ADMIN; positive dish ID |
| PUT `/api/admin/dish/{id}` | ADMIN; complete dish body; nullable imageUrl/calories/tags can be cleared |
| DELETE `/api/admin/dish/{id}` | ADMIN; sets OFF_SALE and logical deletion; missing/deleted row returns 404 |
| POST `/api/admin/menu-daily` | ADMIN; school plus menu body below; SCHOOL display/favorites only |
| GET `/api/parent/dish` | PARENT with a current family; page/pageSize, optional positive categoryId and keyword (1-64); returns ON_SALE dishes only |
| GET `/api/parent/menu-daily` | PARENT with a current family; menuDate and mealType; returns only the authenticated parent's current-family menu |
| POST `/api/parent/menu-daily` | PARENT; menu body only; school/familyId/ownerKey/sourceType/childId cannot override ownership |
| GET `/api/menu/daily` | CHILD or PARENT; sourceType, menuDate, mealType; parent must supply authorized childId for preview |
| POST `/api/menu/mark-favorite` | CHILD self only; positive dishId and required boolean favorite |
| GET `/api/child/preferences` | Existing CHILD-self/PARENT-scoped endpoint; adds favoriteDishIds |
| PUT `/api/child/preferences` | Existing self-only dislikes/tastes write; response adds favoriteDishIds, request whitelist unchanged |

Pagination defaults to page 1/pageSize 20, accepts page >=1 and pageSize 1-100,
and returns items/total/page/pageSize. Empty pages return an empty array.
All catalog body DTOs reject unknown fields. Menus accept 1-50 positive dish IDs;
duplicate menu IDs are deduplicated preserving first occurrence. Order lines
instead reject duplicates.

Dish request example:

```json
{
  "categoryId": "1",
  "name": "Vegetable rice",
  "imageUrl": null,
  "virtualPrice": "18.00",
  "calories": null,
  "tags": "vegetable",
  "allergens": [],
  "allergenStatus": "DECLARED",
  "spiceLevel": 0,
  "status": "ON_SALE"
}
```

Dish response example:

```json
{
  "code": 0,
  "data": {
    "dishId": "11",
    "categoryId": "1",
    "name": "Vegetable rice",
    "imageUrl": null,
    "virtualPrice": "18.00",
    "calories": null,
    "tags": "vegetable",
    "allergens": [],
    "allergenStatus": "DECLARED",
    "spiceLevel": 0,
    "status": "ON_SALE"
  },
  "message": "success",
  "requestId": "catalog-example"
}
```

Prices must be between 0.00 and 99999999.99 with at most two fractional digits.
Categories must exist and be ENABLED. Allergen arrays accept at most 20 unique,
published codes. A blank catalog approval reference blocks catalog declaration
writes and marks menu safety UNKNOWN. Spice is integer 0-3; calories, when
supplied, are a nonnegative integer; optional image URLs must be HTTP(S) with a
host and no credentials.

FAMILY menu request:

```json
{"menuDate":"2026-09-18","mealType":"LUNCH","dishIds":["11"],"status":"PUBLISHED"}
```

SCHOOL requests add `school`; accepted meals are BREAKFAST/LUNCH/DINNER. Status
defaults to PUBLISHED and can be DRAFT. Upsert response:

```json
{"code":0,"data":{"menuId":"21"},"message":"success","requestId":"catalog-example"}
```

Parent catalog query example:

```http
GET /api/parent/dish?page=1&pageSize=100&keyword=rice
Authorization: Bearer <parent-token>
```

```json
{
  "code": 0,
  "data": {
    "items": [{
      "dishId": "11",
      "categoryId": "1",
      "name": "Vegetable rice",
      "imageUrl": null,
      "virtualPrice": "18.00",
      "calories": null,
      "tags": "vegetable",
      "allergens": [],
      "allergenStatus": "DECLARED",
      "spiceLevel": 0,
      "status": "ON_SALE"
    }],
    "total": 1,
    "page": 1,
    "pageSize": 100
  },
  "message": "success",
  "requestId": "catalog-example"
}
```

Parent current-family menu query example:

```http
GET /api/parent/menu-daily?menuDate=2026-09-18&mealType=LUNCH
Authorization: Bearer <parent-token>
```

```json
{
  "code": 0,
  "data": {
    "menuId": "21",
    "menuDate": "2026-09-18",
    "mealType": "LUNCH",
    "status": "PUBLISHED",
    "dishes": [{
      "dishId": "11",
      "categoryId": "1",
      "name": "Vegetable rice",
      "imageUrl": null,
      "virtualPrice": "18.00",
      "calories": null,
      "tags": "vegetable",
      "allergens": [],
      "allergenStatus": "DECLARED",
      "spiceLevel": 0,
      "status": "ON_SALE"
    }],
    "missingDishIds": ["12"]
  },
  "message": "success",
  "requestId": "catalog-example"
}
```

Both parent reads require an authenticated parent with a current bound family.
Family ownership is always derived from the login context; neither read accepts
`familyId`. The maintenance query does not require a child profile or consent,
returns OFF_SALE referenced dishes for explicit cleanup, and reports logically
deleted references in `missingDishIds`. An absent menu is a successful empty
result (`data: null`) so the maintenance page can start a new menu without
surfacing an expected 404 as a network error.

Child menu query example:

```http
GET /api/menu/daily?sourceType=FAMILY&menuDate=2026-09-18&mealType=LUNCH
Authorization: Bearer <child-token>
```

```json
{
  "code": 0,
  "data": {
    "menuId": "21",
    "sourceType": "FAMILY",
    "menuDate": "2026-09-18",
    "mealType": "LUNCH",
    "status": "PUBLISHED",
    "canSubmit": true,
    "dishes": [{
      "dishId": "11",
      "categoryId": "1",
      "name": "Vegetable rice",
      "imageUrl": null,
      "virtualPrice": "18.00",
      "calories": null,
      "tags": "vegetable",
      "allergens": [],
      "allergenStatus": "DECLARED",
      "spiceLevel": 0,
      "status": "ON_SALE",
      "isDisliked": false,
      "isFavorite": false,
      "allergyConflict": false,
      "canSelect": true,
      "safetyStatus": "DECLARED"
    }],
    "missingDishIds": []
  },
  "message": "success",
  "requestId": "catalog-example"
}
```

`canSubmit` means this child may submit at least one currently eligible item,
not that all displayed dishes are eligible. Per-item `canSelect` is false for
SCHOOL, parent previews, stale dates, UNKNOWN, ALLERGY_CONFLICT, and OFF_SALE.
Deleted references appear in `missingDishIds` rather than silently disappearing.
Unpublished or absent menus return 404. Daily display requires bound child,
live consent and COMPLETE profile, and never accepts client school/family scope.

Favorite request:

```json
{"dishId":"11","favorite":true}
```

```json
{
  "code": 0,
  "data": {
    "childId": "31",
    "dislikes": [],
    "tastes": [],
    "favoriteDishIds": ["11"]
  },
  "message": "success",
  "requestId": "catalog-example"
}
```

Repeated favorite/unfavorite operations are idempotent. Favorites are independent
of menu dates. UNKNOWN dishes can be favorited; that does not bypass order safety.
Adding a deleted/missing dish returns 404; removing it remains possible.
Profile and preferences updates preserve the collection. Parent mutation or
another child's identity injection is rejected. Revoked consent blocks reads
and writes with E-010.

## Migration

`sql/sprint3_catalog.sql` runs once after Sprint 1 initialization/cutover and
before code using `ChildProfile.favoriteDishIds` is enabled. The integrating
parent owns shared test resource/schema loading; this module does not change
pom.xml, application-test.yml, BaseIT, or Sprint 1 fixtures.

The incremental ALTER adds `favorite_dish_ids JSON NOT NULL DEFAULT
(JSON_ARRAY())`, which supplies empty collections for existing and newly
inserted profiles. The Java entity intentionally has no field initializer:
existing partial-update objects must not accidentally clear stored favorites.
The script then creates life_dish_category, life_dish and life_menu_daily with
sort/category/price/family indexes, enum/range checks, restrictive foreign keys
and the unique source/owner/date/meal key. There is no cascading deletion.

This is an additive migration for a completed Sprint 1 database without catalog
tables. It is not a legacy catalog conversion or a rerunnable bootstrap. Before
applying, check migration history, existing columns/tables, backups, SQL mode,
row counts and MySQL JSON-expression-default/CHECK support. Execute only once;
DDL can commit independently, so record partial completion and resume reviewed
steps rather than rerunning the file blindly. Test ALTER/index lock duration on
representative staging data. Existing allergen_flag or incompatible catalog
rows need a separately reviewed expansion/backfill; do not overwrite them or
assume they imply DECLARED. Roll application code back without dropping
favorites/catalog columns, menu IDs or historical references.

## Tests and Limits

`CatalogFlowIT extends BaseIT` covers role isolation, dish/category validation,
price/ID JSON contracts, pagination, stable menu IDs, ownership injection,
SCHOOL restriction, parent catalog and current-family maintenance reads,
cross-family isolation, parent previews, safety/dislike/missing display, line
bounds, wrong date/DRAFT/deleted/downlisted dishes, rollback of failed
publication, favorites default/idempotency/preservation/authorization, missing
profile and revocation, unpublished allergen catalog, concurrent first
publication and favorite updates, and both orderings of dish mutation versus
validation locks.

It uses the parent's global schema loader and the existing synthetic MySQL/Redis
Testcontainers setup, not configured business databases. All 14 catalog cases
passed in the final full integration run, including clearing nullable PUT fields.
The parent-maintenance additions compile under JDK 21; their focused integration
run still requires an available Docker/Testcontainers environment.

Operational limits: no business-data migration execution, deployment,
frontend acceptance, nutritional source verification, ingredient inference,
or production safety certification. `isDisliked` is only a text hint against
name/tags, not a safety rule. No new error codes were added: invalid inputs use
E-400, missing resources E-404, forbidden scope E-009, stale/unsafe catalog state
E-007, incomplete profile E-002, and invalid consent E-010.

Audits use existing request IDs and record catalog actions or consent IDs without
copying child allergies/preferences into logs. For operational diagnosis, retain
requestId/errorCode/action/targetId and transaction failure metrics; never log
request bodies or child safety arrays.

## Owned Files

Paths below are relative to `growth-planet/`.

- `sql/sprint3_catalog.sql`
- `src/main/java/cn/studykid/growthplanet/entity/DishCategory.java`
- `src/main/java/cn/studykid/growthplanet/entity/Dish.java`
- `src/main/java/cn/studykid/growthplanet/entity/MenuDaily.java`
- `src/main/java/cn/studykid/growthplanet/entity/ChildProfile.java` (favorite field only)
- `src/main/java/cn/studykid/growthplanet/mapper/DishCategoryMapper.java`
- `src/main/java/cn/studykid/growthplanet/mapper/DishMapper.java`
- `src/main/java/cn/studykid/growthplanet/mapper/MenuDailyMapper.java`
- `src/main/java/cn/studykid/growthplanet/service/CatalogService.java`
- `src/main/java/cn/studykid/growthplanet/controller/MenuController.java` (catalog only)
- `src/main/java/cn/studykid/growthplanet/dto/request/DishCategoryReq.java`
- `src/main/java/cn/studykid/growthplanet/dto/request/DishReq.java`
- `src/main/java/cn/studykid/growthplanet/dto/request/MenuDailyReq.java`
- `src/main/java/cn/studykid/growthplanet/dto/request/MarkFavoriteReq.java`
- `src/main/java/cn/studykid/growthplanet/dto/request/OrderLineReq.java`
- `src/main/java/cn/studykid/growthplanet/dto/response/DishCategoryResp.java`
- `src/main/java/cn/studykid/growthplanet/dto/response/DishResp.java`
- `src/main/java/cn/studykid/growthplanet/dto/response/MenuDailyResp.java`
- `src/main/java/cn/studykid/growthplanet/dto/response/MenuMaintenanceResp.java`
- `src/main/java/cn/studykid/growthplanet/dto/response/MenuUpsertResp.java`
- `src/main/java/cn/studykid/growthplanet/dto/response/ChildPreferencesResp.java` (favorite field only)
- `src/main/java/cn/studykid/growthplanet/service/impl/AuthServiceImpl.java` (preferencesResponse mapping only)
- `src/test/java/cn/studykid/growthplanet/CatalogFlowIT.java`
- `docs/sprint3-catalog.md`

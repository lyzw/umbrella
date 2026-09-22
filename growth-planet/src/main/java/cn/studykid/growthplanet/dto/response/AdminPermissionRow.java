package cn.studykid.growthplanet.dto.response;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 角色权限矩阵的一行：资源 + 各操作是否拥有。
 */
public class AdminPermissionRow {

    private String resource;
    private Map<String, Boolean> perms = new LinkedHashMap<>();

    public AdminPermissionRow(String resource) {
        this.resource = resource;
    }

    public void put(String action, boolean granted) {
        perms.put(action, granted);
    }

    public String getResource() {
        return resource;
    }

    public Map<String, Boolean> getPerms() {
        return perms;
    }
}

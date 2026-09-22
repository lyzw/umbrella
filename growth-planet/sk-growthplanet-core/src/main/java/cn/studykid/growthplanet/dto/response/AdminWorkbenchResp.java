package cn.studykid.growthplanet.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 运营工作台聚合数据（GET /api/console/workbench）。
 * 地基切片先落地「概览 KPI + 待办入口」，各待办项的明细列表由对应模块后续里程碑补充。
 */
public class AdminWorkbenchResp {

    private LocalDateTime generatedAt;

    /** 概览 KPI。 */
    private Kpis kpis;

    /** 待办入口（module + 文案 + 数量 + 目标路由）。 */
    private List<TodoItem> todos;

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public Kpis getKpis() {
        return kpis;
    }

    public List<TodoItem> getTodos() {
        return todos;
    }

    /** 概览指标。 */
    public static class Kpis {
        private long familyTotal;
        private long childTotal;
        private long adminAccountTotal;
        private long pendingConfirmTotal;
        private long pendingPrivacyTotal;

        public Kpis(long familyTotal, long childTotal, long adminAccountTotal,
                    long pendingConfirmTotal, long pendingPrivacyTotal) {
            this.familyTotal = familyTotal;
            this.childTotal = childTotal;
            this.adminAccountTotal = adminAccountTotal;
            this.pendingConfirmTotal = pendingConfirmTotal;
            this.pendingPrivacyTotal = pendingPrivacyTotal;
        }

        public long getFamilyTotal() {
            return familyTotal;
        }

        public long getChildTotal() {
            return childTotal;
        }

        public long getAdminAccountTotal() {
            return adminAccountTotal;
        }

        public long getPendingConfirmTotal() {
            return pendingConfirmTotal;
        }

        public long getPendingPrivacyTotal() {
            return pendingPrivacyTotal;
        }
    }

    /** 待办项。 */
    public static class TodoItem {
        private String module;
        private String label;
        private long count;
        private String route;

        public TodoItem(String module, String label, long count, String route) {
            this.module = module;
            this.label = label;
            this.count = count;
            this.route = route;
        }

        public String getModule() {
            return module;
        }

        public String getLabel() {
            return label;
        }

        public long getCount() {
            return count;
        }

        public String getRoute() {
            return route;
        }
    }

    public static AdminWorkbenchResp of(Kpis kpis, List<TodoItem> todos) {
        AdminWorkbenchResp r = new AdminWorkbenchResp();
        r.generatedAt = LocalDateTime.now();
        r.kpis = kpis;
        r.todos = todos;
        return r;
    }
}

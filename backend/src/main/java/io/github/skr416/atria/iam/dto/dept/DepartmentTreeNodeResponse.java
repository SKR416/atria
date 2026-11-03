package io.github.skr416.atria.iam.dto.dept;

import io.github.skr416.atria.iam.model.Department;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Hierarchical tree node response representing a department and its children (ADR-0004).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentTreeNodeResponse {

    private Long id;
    private Long parentId;
    private String treePath;
    private String code;
    private Map<String, String> name;
    private String localizedName;
    private Long leaderId;
    private String timezone;
    private int sortOrder;

    @Builder.Default
    private List<DepartmentTreeNodeResponse> children = new ArrayList<>();

    public static DepartmentTreeNodeResponse from(Department dept, Locale locale) {
        return DepartmentTreeNodeResponse.builder()
                .id(dept.getId())
                .parentId(dept.getParentId())
                .treePath(dept.getTreePath())
                .code(dept.getCode())
                .name(dept.getName())
                .localizedName(resolveLocalizedName(dept.getName(), locale))
                .leaderId(dept.getLeaderId())
                .timezone(dept.getTimezone())
                .sortOrder(dept.getSortOrder())
                .children(new ArrayList<>())
                .build();
    }

    public static String resolveLocalizedName(Map<String, String> nameMap, Locale locale) {
        if (nameMap == null || nameMap.isEmpty()) {
            return "";
        }
        String langTag = locale != null ? locale.toLanguageTag() : "zh-TW";
        if (nameMap.containsKey(langTag)) {
            return nameMap.get(langTag);
        }
        if (langTag.contains("-")) {
            String baseLang = langTag.split("-")[0];
            for (Map.Entry<String, String> entry : nameMap.entrySet()) {
                if (entry.getKey().startsWith(baseLang)) {
                    return entry.getValue();
                }
            }
        }
        if (nameMap.containsKey("zh-TW")) {
            return nameMap.get("zh-TW");
        }
        if (nameMap.containsKey("en-US")) {
            return nameMap.get("en-US");
        }
        if (nameMap.containsKey("ja-JP")) {
            return nameMap.get("ja-JP");
        }
        return nameMap.values().iterator().next();
    }
}

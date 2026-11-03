package io.github.skr416.atria.iam.dto.role;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Locale;
import java.util.Map;

/**
 * Response payload describing a system permission.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionResponse {

    private Long id;
    private String code;
    private String module;
    private Map<String, String> name;
    private String localizedName;

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

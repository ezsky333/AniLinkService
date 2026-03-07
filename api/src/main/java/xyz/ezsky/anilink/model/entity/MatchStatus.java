package xyz.ezsky.anilink.model.entity;

/**
 * 媒体文件的匹配状态枚举
 */
public enum MatchStatus {
    /**
     * 未匹配过：0
     */
    UNMATCHED(0, "未匹配"),

    /**
     * 匹配完成：1
     */
    MATCHED(1, "已匹配"),

    /**
     * 尝试匹配但无匹配项：2
     */
    NO_MATCH_FOUND(2, "未找到匹配");

    private final int value;
    private final String description;

    MatchStatus(int value, String description) {
        this.value = value;
        this.description = description;
    }

    public int getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据整数值返回对应的 MatchStatus
     */
    public static MatchStatus fromValue(int value) {
        for (MatchStatus status : MatchStatus.values()) {
            if (status.value == value) {
                return status;
            }
        }
        return UNMATCHED; // 默认返回未匹配
    }
}

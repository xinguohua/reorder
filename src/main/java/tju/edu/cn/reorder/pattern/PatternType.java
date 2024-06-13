package tju.edu.cn.reorder.pattern;

public enum PatternType {
    Cross,
    Single,
    Initial;

    public static PatternType fromString(String type) {
        if (type == null) {
            throw new IllegalArgumentException("Type cannot be null");
        }
        switch (type.toLowerCase()) {
            case "cross":
                return Cross;
            case "single":
                return Single;
            case "initial":
                return Initial;
            default:
                throw new IllegalArgumentException("Unknown type: " + type);
        }
    }
}

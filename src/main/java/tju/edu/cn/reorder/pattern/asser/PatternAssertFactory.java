package tju.edu.cn.reorder.pattern.asser;

import tju.edu.cn.reorder.pattern.PatternType;

public class PatternAssertFactory {
    public static PatternAssert getPatternBuilder(String strategyType) {
        PatternType patternType = PatternType.fromString(strategyType);
        switch (patternType) {
            case Cross:
                return new CrossPatternAssert();
            case Single:
                return new SinglePatternAssert();
            case Initial:
                return new InitialPatternAssert();
            default:
                throw new IllegalArgumentException("Unknown strategy type: " + strategyType);
        }
    }
}

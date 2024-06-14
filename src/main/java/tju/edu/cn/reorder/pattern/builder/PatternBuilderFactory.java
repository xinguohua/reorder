package tju.edu.cn.reorder.pattern.builder;

import tju.edu.cn.reorder.SimpleSolver;
import tju.edu.cn.reorder.pattern.PatternType;

public class PatternBuilderFactory {
    public static PatternBuilder getPatternBuilder(String strategyType, SimpleSolver solver) {
        PatternType patternType = PatternType.fromString(strategyType);
        switch (patternType) {
            case Cross:
                return new CrossPatternBuilder(solver);
            case Single:
                return new SinglePatternBuilder(solver);
            case Initial:
                return new InitialPatternBuilder(solver);
            default:
                throw new IllegalArgumentException("Unknown strategy type: " + strategyType);
        }
    }
}

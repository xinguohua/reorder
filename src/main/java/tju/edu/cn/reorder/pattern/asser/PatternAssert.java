package tju.edu.cn.reorder.pattern.asser;

import tju.edu.cn.reorder.pattern.builder.SearchContext;

public interface PatternAssert {

    String buildReorderAssert(AssertContext assertContext);

    AssertContext buildAssertContext(SearchContext searchContext);
}

package tju.edu.cn.reorder.pattern.asser;

import tju.edu.cn.reorder.pattern.builder.SearchContext;
import tju.edu.cn.reorder.trace.Indexer;
import tju.edu.cn.trace.MemAccNode;
import tju.edu.cn.trace.ReadNode;

import java.util.ArrayList;

public class SinglePatternAssert extends AbstractPatternAssert {
    public ArrayList<String> generateCombinations(String violateStr1, String violateStr2, String obeyStr1, String obeyStr2) {
        ArrayList<String> combinations = new ArrayList<>();
        if (violateStr1 != null && !violateStr1.isEmpty()) {
            combinations.add("(and " + violateStr1 + " " + obeyStr2  + ")\n");
        }
        if (violateStr2 != null && !violateStr2.isEmpty()) {
            combinations.add("(and " + obeyStr1 + " " + violateStr2 + ")\n");
        }
        return combinations;
    }

    public String buildFinalAssert(ArrayList<String> combinations) {
        StringBuilder finalAssert = new StringBuilder("(assert (or ");
        for (String combination : combinations) {
            finalAssert.append(combination.trim()).append(" ");
        }
        finalAssert.append("))\n");
        return finalAssert.toString();
    }



    @Override
    public String buildReorderAssert(AssertContext assertContext) {
        Indexer currentIndexer = assertContext.getCurrentIndexer();
        MemAccNode switchNode1 = assertContext.getSwitchNode1();
        MemAccNode switchNode2 = assertContext.getSwitchNode2();
        ArrayList<ReadNode> swapReadNodes1 = new ArrayList<>();
        currentIndexer.getReorderDependentRead1(swapReadNodes1, switchNode1);
        String obeyStr1 = buildReorderConstrOpt(currentIndexer, swapReadNodes1, false);
        String violateStr1 = buildReorderConstrOpt(currentIndexer, swapReadNodes1, true);


        ArrayList<ReadNode> swapReadNodes2 = new ArrayList<>();
        currentIndexer.getReorderDependentRead1(swapReadNodes2, switchNode2);
        String obeyStr2 = buildReorderConstrOpt(currentIndexer, swapReadNodes2, false);
        String violateStr2 = buildReorderConstrOpt(currentIndexer, swapReadNodes2, true);

        ArrayList<String> combinations = generateCombinations(violateStr1, violateStr2, obeyStr1, obeyStr2);
        if (combinations.isEmpty()) return null;
        return buildFinalAssert(combinations);
    }

    @Override
    public AssertContext buildAssertContext(SearchContext searchContext) {
        AssertContext assertContext = new AssertContext();
        assertContext.setCurrentIndexer(searchContext.getCurrentIndexer());
        assertContext.setSwitchNode1(searchContext.getSwitchPair().key);
        assertContext.setSwitchNode2(searchContext.getSwitchPair().value);
        return assertContext;
    }
}

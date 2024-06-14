package tju.edu.cn.reorder.pattern.asser;

import tju.edu.cn.reorder.pattern.builder.SearchContext;
import tju.edu.cn.reorder.trace.Indexer;
import tju.edu.cn.trace.MemAccNode;
import tju.edu.cn.trace.ReadNode;

import java.util.ArrayList;

public class CrossPatternAssert extends AbstractPatternAssert {
    @Override
    public String buildReorderAssert(AssertContext assertContext) {
        Indexer currentIndexer = assertContext.getCurrentIndexer();
        MemAccNode switchNode1 = assertContext.getSwitchNode1();
        MemAccNode switchNode2 = assertContext.getSwitchNode2();
        MemAccNode dependNode1 = assertContext.getDependNode1();
        MemAccNode dependNode2 = assertContext.getDependNode2();
        ArrayList<ReadNode> dependReadNodes1 = new ArrayList<>();
        currentIndexer.getReorderDependentRead1(dependReadNodes1, dependNode1);
        String obeyStr1 = buildReorderConstrOpt(currentIndexer, dependReadNodes1, false);
        String violateStr1 = buildReorderConstrOpt(currentIndexer, dependReadNodes1, true);

        ArrayList<ReadNode> dependReadNodes2 = new ArrayList<>();
        currentIndexer.getReorderDependentRead1(dependReadNodes2, dependNode2);
        String obeyStr2 = buildReorderConstrOpt(currentIndexer, dependReadNodes2, false);
        String violateStr2 = buildReorderConstrOpt(currentIndexer, dependReadNodes2, true);


        ArrayList<ReadNode> swapReadNodes1 = new ArrayList<>();
        currentIndexer.getReorderDependentRead1(swapReadNodes1, switchNode1);
        String obeyStr3 = buildReorderConstrOpt(currentIndexer, swapReadNodes1, false);
        String violateStr3 = buildReorderConstrOpt(currentIndexer, swapReadNodes1, true);


        ArrayList<ReadNode> swapReadNodes2 = new ArrayList<>();
        currentIndexer.getReorderDependentRead1(swapReadNodes2, switchNode2);
        String obeyStr4 = buildReorderConstrOpt(currentIndexer, swapReadNodes2, false);
        String violateStr4 = buildReorderConstrOpt(currentIndexer, swapReadNodes2, true);

        ArrayList<String> combinations = generateCombinations(violateStr1, violateStr2, violateStr3, violateStr4, obeyStr1, obeyStr2, obeyStr3, obeyStr4);
        if (combinations.isEmpty()) return null;
        return buildFinalAssert(combinations);
    }

    @Override
    public AssertContext buildAssertContext(SearchContext searchContext) {
        AssertContext assertContext = new AssertContext();
        assertContext.setCurrentIndexer(searchContext.getCurrentIndexer());
        assertContext.setSwitchNode1(searchContext.getSwitchPair().key);
        assertContext.setSwitchNode2(searchContext.getSwitchPair().value);
        assertContext.setDependNode1(searchContext.getDependPair().key);
        assertContext.setDependNode2(searchContext.getDependPair().value);
        return assertContext;
    }

    public ArrayList<String> generateCombinations(String violateStr1, String violateStr2, String violateStr3, String violateStr4, String obeyStr1, String obeyStr2, String obeyStr3, String obeyStr4) {
        ArrayList<String> combinations = new ArrayList<>();
        if (violateStr1 != null && !violateStr1.isEmpty()) {
            combinations.add("(and " + violateStr1 + " " + obeyStr2 + " " + obeyStr3 + " " + obeyStr4 + ")\n");
        }
        if (violateStr2 != null && !violateStr2.isEmpty()) {
            combinations.add("(and " + obeyStr1 + " " + violateStr2 + " " + obeyStr3 + " " + obeyStr4 + ")\n");
        }
        if (violateStr3 != null && !violateStr3.isEmpty()) {
            combinations.add("(and " + obeyStr1 + " " + obeyStr2 + " " + violateStr3 + " " + obeyStr4 + ")\n");
        }
        if (violateStr4 != null && !violateStr4.isEmpty()) {
            combinations.add("(and " + obeyStr1 + " " + obeyStr2 + " " + obeyStr3 + " " + violateStr4 + ")\n");
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


}

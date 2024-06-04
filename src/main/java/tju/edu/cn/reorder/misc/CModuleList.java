package tju.edu.cn.reorder.misc;

import java.util.ArrayList;

public class CModuleList {
	
  private final ArrayList<CModuleSection> modules = new ArrayList<>(20);

  public boolean excludeLib = false;

  public void addMainExe(CModuleSection m) {
    modules.add(m);
  }


  public void add(CModuleSection m) {
    modules.add(m);
  }

  public int size() {
    return modules.size();
  }

  public Pair<String, Long> findNameAndOffset(long pc) {
    for (CModuleSection m : modules) {
      if (m.begin <= pc && pc < m.end) {
        return new Pair<>(m.name, pc - m.base);
      }
    }
    return null;
  }

  @Override
  public String toString() {
    return "CModuleList{" +
        "modules=" + modules +
        '}';
  }
}

package org.gsoft.showcase.diff.logic;

import java.util.List;

public interface DiffGenerator {
    List<DiffListItem> generate(String contentA, String contentB);
}

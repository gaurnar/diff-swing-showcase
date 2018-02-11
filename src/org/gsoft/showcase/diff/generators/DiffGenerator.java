package org.gsoft.showcase.diff.generators;

import java.util.List;

public interface DiffGenerator {
    /**
     * Generate diff based on encoded strings.
     * Use {@link DiffGeneratorUtils} methods for string encoding.
     *
     * @param a "before" encoded string
     * @param b "after" encoded string
     * @return diff items
     */
    List<DiffItem> generate(int[] a, int[] b);
}

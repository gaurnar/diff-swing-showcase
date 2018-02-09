package org.gsoft.showcase.diff.generators;

import java.util.List;

public interface DiffGenerator {
    enum ItemType {
        EQUAL,
        INSERT,
        DELETE
    }

    final class DiffItem {
        private final ItemType type;
        private final int[] chars;

        public DiffItem(ItemType type, int[] chars) {
            this.type = type;
            this.chars = chars;
        }

        public ItemType getType() {
            return type;
        }

        public int[] getChars() {
            return chars.clone();
        }
    }

    List<DiffItem> generate(int[] a, int[] b);
}

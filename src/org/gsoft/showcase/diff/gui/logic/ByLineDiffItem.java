package org.gsoft.showcase.diff.gui.logic;

import org.gsoft.showcase.diff.generators.DiffItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Logically extends {@link DiffItem}.
 *
 * TODO convert to type hierarchy?
 */
public final class ByLineDiffItem {
    private final ExtendedDiffItemType type;

    /**
     * Not set for ExtendedDiffItemType.MODIFIED
     */
    private final String[] strings;

    /**
     * Only set for ExtendedDiffItemType.MODIFIED
     */
    private final List<DiffItem> byCharDiffItems;

    public ByLineDiffItem(ExtendedDiffItemType type, String[] strings,
                          List<DiffItem> byCharDiffItems) {
        if ((strings != null) && (type == ExtendedDiffItemType.MODIFIED)) {
            throw new IllegalArgumentException("strings parameter is forbidden for MODIFIED items");
        }
        if ((byCharDiffItems != null) && (type != ExtendedDiffItemType.MODIFIED)) {
            throw new IllegalArgumentException("byCharDiffItems parameter is only allowed for MODIFIED items");
        }

        this.type = type;
        this.strings = strings;
        this.byCharDiffItems = byCharDiffItems != null ? new ArrayList<>(byCharDiffItems) : null;
    }

    public ExtendedDiffItemType getType() {
        return type;
    }

    public String[] getStrings() {
        if (strings == null) {
            return null;
        }
        return strings.clone();
    }

    public List<DiffItem> getByCharDiffItems() {
        if (byCharDiffItems == null) {
            return null;
        }
        return Collections.unmodifiableList(byCharDiffItems);
    }
}

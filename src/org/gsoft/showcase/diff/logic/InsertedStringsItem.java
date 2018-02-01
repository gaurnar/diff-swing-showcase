package org.gsoft.showcase.diff.logic;

public class InsertedStringsItem implements DiffListItem {
    private final String strings;

    public InsertedStringsItem(String strings) {
        this.strings = strings;
    }

    public String getStrings() {
        return strings;
    }

    @Override
    public void accept(DiffListItemVisitor visitor) {
        visitor.visit(this);
    }
}

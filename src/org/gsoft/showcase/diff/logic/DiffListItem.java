package org.gsoft.showcase.diff.logic;

public interface DiffListItem {
    void accept(DiffListItemVisitor visitor);
}

package org.gsoft.showcase.diff.logic;

public interface DiffListItemVisitor {
    void visit(EqualStringsItem item);
    void visit(DeletedStringsItem item);
    void visit(InsertedStringsItem item);
}

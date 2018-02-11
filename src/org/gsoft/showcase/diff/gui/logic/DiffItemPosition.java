package org.gsoft.showcase.diff.gui.logic;

public final class DiffItemPosition {
    private final int startA, startB;
    private final int endA, endB;
    private final ExtendedDiffItemType type;

    public DiffItemPosition(int startA, int startB,
                            int endA, int endB,
                            ExtendedDiffItemType type) {
        this.startA = startA;
        this.startB = startB;
        this.endA = endA;
        this.endB = endB;
        this.type = type;
    }

    public int getStartA() {
        return startA;
    }

    public int getStartB() {
        return startB;
    }

    public int getEndA() {
        return endA;
    }

    public int getEndB() {
        return endB;
    }

    public ExtendedDiffItemType getType() {
        return type;
    }
}

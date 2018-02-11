package org.gsoft.showcase.diff.generators;

public final class DiffItem {
    private final DiffItemType type;
    private final int[] chars;

    public DiffItem(DiffItemType type, int[] chars) {
        this.type = type;
        this.chars = chars;
    }

    public DiffItemType getType() {
        return type;
    }

    /**
     * @return encoded chars/lines
     * @see DiffGeneratorUtils#decodeString(int[])
     * @see DiffGeneratorUtils#decodeLines(int[], org.gsoft.showcase.diff.generators.DiffGeneratorUtils.LinesEncoding)
     */
    public int[] getChars() {
        return chars.clone();
    }
}

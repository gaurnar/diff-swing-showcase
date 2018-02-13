package org.gsoft.showcase.diff.gui;

import java.awt.*;

public final class Colors {
    public static final Color DELETED_LINES_HIGHLIGHT_COLOR = new Color(250, 180, 170);
    public static final Color INSERTED_LINES_HIGHLIGHT_COLOR = new Color(174, 255, 202);
    public static final Color MODIFIED_LINES_HIGHLIGHT_COLOR = new Color(221, 226, 255);
    public static final Color MODIFIED_CHARS_HIGHLIGHT_COLOR = new Color(168, 191, 234);

    public static final Color CHANGE_HIGHLIGHT_COLOR = Color.BLUE;

    private Colors() {
        throw new UnsupportedOperationException();
    }
}

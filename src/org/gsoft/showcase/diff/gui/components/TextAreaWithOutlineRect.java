package org.gsoft.showcase.diff.gui.components;

import javax.swing.*;
import java.awt.*;

public class TextAreaWithOutlineRect extends JTextArea {
    private static final Stroke HIGHLIGHT_OUTLINE_STROKE = new BasicStroke(2);

    private final Color highlightColor;

    private Rectangle highlightRect;

    public TextAreaWithOutlineRect(Color highlightColor) {
        this.highlightColor = highlightColor;
    }

    public Rectangle getHighlightRect() {
        return highlightRect;
    }

    public void setHighlightRect(Rectangle highlightRect) {
        this.highlightRect = highlightRect;
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);

        if (highlightRect != null) {
            g.setColor(highlightColor);
            ((Graphics2D) g).setStroke(HIGHLIGHT_OUTLINE_STROKE);
            g.drawRect(highlightRect.x, highlightRect.y, highlightRect.width, highlightRect.height);
        }
    }
}

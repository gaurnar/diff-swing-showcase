package org.gsoft.showcase.diff.gui;

import javax.swing.plaf.TextUI;
import javax.swing.text.BadLocationException;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import java.awt.*;

/**
 * Used to highlight lines insertion or deletion points in diff text areas
 */
public class InsertOrDeletePointHighlighter implements Highlighter.HighlightPainter {
    private static final Stroke HIGHLIGHT_STROKE = new BasicStroke(2);

    private final Color color;

    public InsertOrDeletePointHighlighter(Color color) {
        this.color = color;
    }

    @Override
    public void paint(Graphics g, int offs0, int offs1, Shape bounds, JTextComponent c) {
        try {
            TextUI mapper = c.getUI();
            Rectangle p0 = mapper.modelToView(c, offs0);
            g.setColor(color);
            ((Graphics2D) g).setStroke(HIGHLIGHT_STROKE);
            g.drawLine(0, p0.y + 1, // + 1 - for better alignment
                    c.getWidth(), p0.y);
        } catch (BadLocationException e) {
            // can't render
        }
    }
}

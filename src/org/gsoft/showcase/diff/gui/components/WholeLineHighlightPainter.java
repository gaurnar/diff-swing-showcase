package org.gsoft.showcase.diff.gui.components;

import javax.swing.plaf.TextUI;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import java.awt.*;

/**
 * Similar to {@link DefaultHighlighter.DefaultHighlightPainter}, but highlights the whole line.
 */
public class WholeLineHighlightPainter implements Highlighter.HighlightPainter {
    private final Color color;

    public WholeLineHighlightPainter(Color color) {
        this.color = color;
    }

    @Override
    public void paint(Graphics g, int offs0, int offs1, Shape bounds, JTextComponent c) {
        try {
            TextUI mapper = c.getUI();
            Rectangle p0 = mapper.modelToView(c, offs0);
            Rectangle p1 = mapper.modelToView(c, offs1);

            g.setColor(color);
            g.fillRect(0, p0.y, c.getWidth(), p1.y + p1.height - p0.y);
        } catch (BadLocationException e) {
            // can't render
        }
    }
}

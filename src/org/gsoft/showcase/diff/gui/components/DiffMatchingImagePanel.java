package org.gsoft.showcase.diff.gui.components;

import org.gsoft.showcase.diff.gui.logic.DiffItemPosition;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static org.gsoft.showcase.diff.gui.Colors.*;

public class DiffMatchingImagePanel extends JPanel {
    private static final int TOP_PADDING = 2; // TODO calculate this

    //
    // N.B.: In DiffItemPosition instances used in DiffPanesScrollController
    // positions are positions of chars in text area, but here we expect Y
    // coordinates instead.
    //
    // TODO use dedicated type to avoid confusion
    //
    private List<DiffItemPosition> itemPositions;

    public DiffMatchingImagePanel() {
        super(new BorderLayout());

        setBackground(Color.WHITE);
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);

        Graphics2D g2d = (Graphics2D) g;
        RenderingHints renderingHints = new RenderingHints(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHints(renderingHints);

        if (itemPositions == null) {
            return;
        }

        for (DiffItemPosition itemPosition : itemPositions) {
            int[] xs, ys;

            switch (itemPosition.getType()) {
                case EQUAL:
                    continue;

                case MODIFIED:
                    xs = new int[] { 0, getWidth(), getWidth(), 0};
                    ys = new int[] { itemPosition.getStartA() + TOP_PADDING, itemPosition.getStartB() + TOP_PADDING,
                            itemPosition.getEndB() + TOP_PADDING, itemPosition.getEndA() + TOP_PADDING};
                    g.setColor(MODIFIED_LINES_HIGHLIGHT_COLOR);
                    break;

                case INSERT:
                    xs = new int[] { 0, getWidth(), getWidth()};
                    ys = new int[] { itemPosition.getStartA() + TOP_PADDING, itemPosition.getStartB() + TOP_PADDING,
                            itemPosition.getEndB() + TOP_PADDING};
                    g.setColor(INSERTED_LINES_HIGHLIGHT_COLOR);
                    break;

                case DELETE:
                    xs = new int[] { 0, getWidth(), 0};
                    ys = new int[] { itemPosition.getStartA() + TOP_PADDING, itemPosition.getStartB() + TOP_PADDING,
                            itemPosition.getEndA() + TOP_PADDING};
                    g.setColor(DELETED_LINES_HIGHLIGHT_COLOR);
                    break;

                default:
                    throw new RuntimeException("unexpected diff item type: " + itemPosition.getType());
            }

            g.fillPolygon(xs, ys, xs.length);
        }
    }

    public List<DiffItemPosition> getItemPositions() {
        return itemPositions;
    }

    public void setItemPositions(List<DiffItemPosition> itemPositions) {
        this.itemPositions = itemPositions;
    }
}

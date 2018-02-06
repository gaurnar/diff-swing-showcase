package org.gsoft.showcase.diff.gui;

import org.gsoft.showcase.diff.logic.DiffGenerator.DiffItem;
import org.gsoft.showcase.diff.logic.DiffGeneratorUtils;
import org.gsoft.showcase.diff.logic.DiffGeneratorUtils.TextsLinesEncoding;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class DiffForm extends JFrame {
    private static final Color DELETED_LINES_HIGHLIGHT_COLOR = new Color(250, 180, 170);
    private static final Color INSERTED_LINES_HIGHLIGHT_COLOR = new Color(174, 255, 202);

    private static final class BoundScrollRange {
        final int startThis;
        final int endThis;
        final int startOther;
        final boolean scrollOther;

        BoundScrollRange(int startThis, int endThis, int startOther, boolean scrollOther) {
            this.startThis = startThis;
            this.endThis = endThis;
            this.startOther = startOther;
            this.scrollOther = scrollOther;
        }
    }

    private static class ScrollListener implements ChangeListener {
        private final List<BoundScrollRange> scrollRanges;
        private final JScrollPane thisScrollPane;
        private final JScrollPane otherScrollPane;

        private ScrollListener boundListener;
        private BoundScrollRange currentScrollRange;
        private boolean ignoreChanges = false;

        public ScrollListener(JScrollPane thisScrollPane,
                              JScrollPane otherScrollPane,
                              List<BoundScrollRange> scrollRanges) {
            this.thisScrollPane = thisScrollPane;
            this.otherScrollPane = otherScrollPane;
            this.scrollRanges = scrollRanges;
        }

        public void setBoundListener(ScrollListener boundListener) {
            this.boundListener = boundListener;
        }

        @Override
        public void stateChanged(ChangeEvent e) {
            if (ignoreChanges) {
                return;
            }

            Point thisCenterPosition = getViewportCenterPosition(thisScrollPane);

            if ((currentScrollRange == null) ||
                    (thisCenterPosition.y < currentScrollRange.startThis) ||
                    (thisCenterPosition.y > currentScrollRange.endThis)) {
                currentScrollRange = findRange(thisCenterPosition.y, scrollRanges);
                if (currentScrollRange == null) {
                    // not found
                    // TODO when can this happen? does not seem to affect UX
                    return;
                }
            }

            Point otherPosition = otherScrollPane.getViewport().getViewPosition();

            if (boundListener != null) {
                boundListener.ignoreChanges = true; // to avoid cycles
            }

            setViewportCenterPosition(otherScrollPane, new Point(otherPosition.x,
                    currentScrollRange.scrollOther ?
                            currentScrollRange.startOther + thisCenterPosition.y - currentScrollRange.startThis
                            : currentScrollRange.startOther
            ));

            if (boundListener != null) {
                SwingUtilities.invokeLater(() -> boundListener.ignoreChanges = false);
            }
        }

        private static Point getViewportCenterPosition(JScrollPane scrollPane) {
            JViewport viewport = scrollPane.getViewport();
            Point topViewPosition = viewport.getViewPosition();
            return new Point(topViewPosition.x, topViewPosition.y + viewport.getHeight() / 2);
        }

        private static void setViewportCenterPosition(JScrollPane scrollPane, Point centerPoint) {
            JViewport viewport = scrollPane.getViewport();
            viewport.setViewPosition(new Point(centerPoint.x, centerPoint.y - viewport.getHeight() / 2));
            scrollPane.repaint();
        }
    }

    private final List<DiffItem> diffItems;

    private JPanel rootPanel;
    private JLabel fileAPathLabel;
    private JLabel fileBPathLabel;
    private JScrollPane fileAScrollPane;
    private JScrollPane fileBScrollPane;

    private List<DiffItemTextPosition> diffItemPositionsA;
    private List<DiffItemTextPosition> diffItemPositionsB;

    private ScrollListener scrollListenerA;
    private ScrollListener scrollListenerB;

    private JTextArea textAreaA;
    private JTextArea textAreaB;

    public DiffForm(String fileAPath, String fileBPath,
                    List<DiffItem> diffItems,
                    TextsLinesEncoding textsLinesEncoding) {
        this.diffItems = diffItems;

        setTitle("Diff");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(800, 600));

        fileAPathLabel.setText(fileAPath);
        fileBPathLabel.setText(fileBPath);

        try {
            populateDiffAreas(textsLinesEncoding);
        } catch (BadLocationException e) {
            throw new RuntimeException(e);
        }

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                try {
                    setupSideBySideScrolling();
                } catch (BadLocationException e1) {
                    throw new RuntimeException(e1);
                }
            }
        });

        setContentPane(rootPanel);
        pack();
    }

    private static class DiffItemTextPosition {
        final int start, end;

        DiffItemTextPosition(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    private void populateDiffAreas(TextsLinesEncoding textsLinesEncoding) throws BadLocationException {
        textAreaA = makeTextArea();
        textAreaB = makeTextArea();

        diffItemPositionsA = new ArrayList<>();
        diffItemPositionsB = new ArrayList<>();

        for (DiffItem item : diffItems) {
            switch (item.getType()) {
                case EQUAL:
                    String[] decodedStrings = decodeStrings(textsLinesEncoding, item);

                    diffItemPositionsA.add(addLinesToTextArea(textAreaA, decodedStrings));
                    diffItemPositionsB.add(addLinesToTextArea(textAreaB, decodedStrings));

                    break;

                case DELETE:
                    DiffItemTextPosition positionA = addLinesToTextArea(textAreaA,
                            decodeStrings(textsLinesEncoding, item));

                    int nextCharPositionB = textAreaB.getLineCount() != 0 ?
                            textAreaB.getLineEndOffset(textAreaB.getLineCount() - 1) + 1
                            : 0;

                    diffItemPositionsA.add(positionA);
                    diffItemPositionsB.add(new DiffItemTextPosition(nextCharPositionB, nextCharPositionB));

                    break;

                case INSERT:
                    DiffItemTextPosition positionB = addLinesToTextArea(textAreaB,
                            decodeStrings(textsLinesEncoding, item));

                    int nextCharPositionA = textAreaA.getLineCount() != 0 ?
                            textAreaA.getLineEndOffset(textAreaA.getLineCount() - 1) + 1
                            : 0;

                    diffItemPositionsA.add(new DiffItemTextPosition(nextCharPositionA, nextCharPositionA));
                    diffItemPositionsB.add(positionB);

                    break;

                default:
                    throw new RuntimeException("unexpected diff item type: " + item.getType());
            }
        }

        int diffPositionIdxA = 0;
        int diffPositionIdxB = 0;

        // for some reason we can not assign highlighters as we append line to text area;
        // doing it in separate cycle
        for (DiffItem item : diffItems) {
            DiffItemTextPosition positionA = diffItemPositionsA.get(diffPositionIdxA++);
            DiffItemTextPosition positionB = diffItemPositionsB.get(diffPositionIdxB++);

            switch (item.getType()) {
                case EQUAL:
                    // no highlighter necessary
                    break;

                case DELETE:
                    textAreaA.getHighlighter().addHighlight(positionA.start, positionA.end,
                            new WholeLineHighlightPainter(DELETED_LINES_HIGHLIGHT_COLOR));
                    textAreaB.getHighlighter().addHighlight(positionB.start, positionB.end,
                            new InsertOrDeletePointHighlighter(DELETED_LINES_HIGHLIGHT_COLOR));

                    break;

                case INSERT:
                    textAreaA.getHighlighter().addHighlight(positionA.start, positionA.end,
                            new InsertOrDeletePointHighlighter(INSERTED_LINES_HIGHLIGHT_COLOR));
                    textAreaB.getHighlighter().addHighlight(positionB.start, positionB.end,
                            new WholeLineHighlightPainter(INSERTED_LINES_HIGHLIGHT_COLOR));

                    break;

                default:
                    throw new RuntimeException("unexpected diff item type: " + item.getType());
            }
        }

        fileAScrollPane.getViewport().setView(textAreaA);
        fileBScrollPane.getViewport().setView(textAreaB);
    }

    private static DiffItemTextPosition addLinesToTextArea(JTextArea textArea, String[] lines) throws BadLocationException {
        int previousLineCount = textArea.getLineCount();
        if (previousLineCount != 0) {
            textArea.append("\n");
        }
        textArea.append(Arrays.stream(lines).collect(Collectors.joining("\n")));
        return new DiffItemTextPosition(textArea.getLineStartOffset(previousLineCount),
                textArea.getLineEndOffset(textArea.getLineCount() - 1));
    }

    private static String[] decodeStrings(TextsLinesEncoding textsLinesEncoding, DiffItem item) {
        return DiffGeneratorUtils.decodeText(item.getChars(), textsLinesEncoding);
    }

    private void setupSideBySideScrolling() throws BadLocationException {
        List<BoundScrollRange> scrollRangesA = new ArrayList<>();
        List<BoundScrollRange> scrollRangesB = new ArrayList<>();

        int diffPositionIdxA = 0;
        int diffPositionIdxB = 0;

        for (DiffItem item : diffItems) {
            JComponent textComponent;
            switch (item.getType()) {
                case EQUAL:
                    DiffItemTextPosition positionA = diffItemPositionsA.get(diffPositionIdxA++);
                    Rectangle firstCharRectA = textAreaA.modelToView(positionA.start);
                    Rectangle lastCharRectA = textAreaA.modelToView(positionA.end);

                    DiffItemTextPosition positionB = diffItemPositionsB.get(diffPositionIdxB++);
                    Rectangle firstCharRectB = textAreaB.modelToView(positionB.start);
                    Rectangle lastCharRectB = textAreaB.modelToView(positionB.end);

                    scrollRangesA.add(new BoundScrollRange(
                            firstCharRectA.getLocation().y,
                            lastCharRectA.getLocation().y + lastCharRectA.height,
                            firstCharRectB.getLocation().y,
                            true
                    ));

                    scrollRangesB.add(new BoundScrollRange(
                            firstCharRectB.getLocation().y,
                            lastCharRectB.getLocation().y + lastCharRectB.height,
                            firstCharRectA.getLocation().y,
                            true
                    ));

                    break;

                case DELETE:
                    positionA = diffItemPositionsA.get(diffPositionIdxA++);
                    firstCharRectA = textAreaA.modelToView(positionA.start);
                    lastCharRectA = textAreaA.modelToView(positionA.end);

                    positionB = diffItemPositionsB.get(diffPositionIdxB++);
                    firstCharRectB = textAreaB.modelToView(positionB.start);

                    scrollRangesA.add(new BoundScrollRange(
                            firstCharRectA.getLocation().y,
                            lastCharRectA.getLocation().y + lastCharRectA.height,
                            firstCharRectB.getLocation().y,
                            false
                    ));

                    break;

                case INSERT:
                    positionB = diffItemPositionsB.get(diffPositionIdxB++);
                    firstCharRectB = textAreaB.modelToView(positionB.start);
                    lastCharRectB = textAreaB.modelToView(positionB.end);

                    positionA = diffItemPositionsA.get(diffPositionIdxA++);
                    firstCharRectA = textAreaA.modelToView(positionA.start);

                    scrollRangesB.add(new BoundScrollRange(
                            firstCharRectB.getLocation().y,
                            lastCharRectB.getLocation().y + lastCharRectB.height,
                            firstCharRectA.getLocation().y,
                            false
                    ));

                    break;

                default:
                    throw new RuntimeException("unexpected diff item type: " + item.getType());
            }
        }

        if (scrollListenerA != null) {
            fileAScrollPane.getViewport().removeChangeListener(scrollListenerA);
        }

        if (scrollListenerB != null) {
            fileBScrollPane.getViewport().removeChangeListener(scrollListenerB);
        }

        scrollListenerA = new ScrollListener(fileAScrollPane, fileBScrollPane, scrollRangesA);
        scrollListenerB = new ScrollListener(fileBScrollPane, fileAScrollPane, scrollRangesB);

        scrollListenerA.setBoundListener(scrollListenerB);
        scrollListenerB.setBoundListener(scrollListenerA);

        fileAScrollPane.getViewport().addChangeListener(scrollListenerA);
        fileBScrollPane.getViewport().addChangeListener(scrollListenerB);
    }

    private JTextArea makeTextArea() {
        JTextArea textArea = new JTextArea();
        textArea.setFont(new Font("Courier New", Font.PLAIN, 11));
        textArea.setEditable(false); // TODO
        textArea.setLineWrap(false);
        textArea.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
        return textArea;
    }

    private static BoundScrollRange findRange(int pos, List<BoundScrollRange> rangesSorted) {
        // TODO use binary search
        for (BoundScrollRange r : rangesSorted) {
            if ((pos >= r.startThis) && (pos <= r.endThis)) {
                return r;
            }
        }
        return null; // not found
    }
}

package org.gsoft.showcase.diff.gui;

import org.gsoft.showcase.diff.logic.DiffGenerator.DiffItem;
import org.gsoft.showcase.diff.logic.DiffGeneratorUtils;
import org.gsoft.showcase.diff.logic.DiffGeneratorUtils.TextsLinesEncoding;
import org.gsoft.showcase.diff.logic.MyersDiffGenerator;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
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
    private static final Color MODIFIED_LINES_HIGHLIGHT_COLOR = new Color(221, 239, 255);
    private static final Color MODIFIED_CHARS_HIGHLIGHT_COLOR = new Color(187, 211, 255);

    private static final class BoundScrollRange {
        final int startThis;
        final int endThis;
        final int startOther;
        final boolean scrollOther;
        final int changeIndex;

        BoundScrollRange(int startThis, int endThis, int startOther, boolean scrollOther, int changeIndex) {
            this.startThis = startThis;
            this.endThis = endThis;
            this.startOther = startOther;
            this.scrollOther = scrollOther;
            this.changeIndex = changeIndex;
        }
    }

    private class ScrollListener implements ChangeListener {
        private final List<BoundScrollRange> scrollRanges;
        private final JScrollPane thisScrollPane;
        private final JScrollPane otherScrollPane;

        private ScrollListener boundListener;
        private BoundScrollRange currentScrollRange;
        private boolean ignoreChanges = false;
        private boolean ignoreIndexChanges = false;

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

            if (!ignoreIndexChanges) {
                currentChangeIndex = currentScrollRange.changeIndex;
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

        public boolean isIgnoreIndexChanges() {
            return ignoreIndexChanges;
        }

        public void setIgnoreIndexChanges(boolean ignoreIndexChanges) {
            this.ignoreIndexChanges = ignoreIndexChanges;
        }
    }

    private static class DiffItemTextPosition {
        final int start, end;

        DiffItemTextPosition(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    /**
     * TODO convert to type hierarchy
     */
    private static final class CharwiseDiffItem {
        final ExtendedDiffItemType type;
        final String chars;
        final String otherChars;

        CharwiseDiffItem(ExtendedDiffItemType type, String chars, String otherChars) {
            this.type = type;
            this.chars = chars;
            this.otherChars = otherChars;
        }
    }

    private enum ExtendedDiffItemType {
        EQUAL,
        INSERT,
        DELETE,
        MODIFIED
    }

    /**
     * TODO convert to type hierarchy
     */
    private static final class LinewiseDiffItem {
        final ExtendedDiffItemType type;
        final String[] strings;
        final List<CharwiseDiffItem> charwiseDiffItems;

        private LinewiseDiffItem(ExtendedDiffItemType type, String[] strings,
                                 List<CharwiseDiffItem> charwiseDiffItems) {
            this.type = type;
            this.strings = strings;
            this.charwiseDiffItems = charwiseDiffItems;
        }
    }

    private final List<LinewiseDiffItem> diffItems;

    private JPanel rootPanel;
    private JLabel fileAPathLabel;
    private JLabel fileBPathLabel;
    private JScrollPane fileAScrollPane;
    private JScrollPane fileBScrollPane;
    private JButton prevChangeButton;
    private JButton nextChangeButton;

    private List<DiffItemTextPosition> diffItemPositionsA;
    private List<DiffItemTextPosition> diffItemPositionsB;

    private ScrollListener scrollListenerA;
    private ScrollListener scrollListenerB;

    private JTextArea textAreaA;
    private JTextArea textAreaB;

    private List<Integer> changePositions; // positions in textAreaA
    private int currentChangeIndex = 0;

    public DiffForm(String fileAPath, String fileBPath,
                    List<DiffItem> diffItems,
                    TextsLinesEncoding textsLinesEncoding) {
        setTitle("Diff");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(800, 600));

        fileAPathLabel.setText(fileAPath);
        fileBPathLabel.setText(fileBPath);

        this.diffItems = convertDiffItems(diffItems, textsLinesEncoding);

        try {
            populateDiffAreas();
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

        prevChangeButton.addActionListener(e -> {
            if (currentChangeIndex > 2) {
                currentChangeIndex -= currentChangeIndex % 2 == 0 ? 2 : 1;
                if (scrollListenerA != null) {
                    scrollListenerA.setIgnoreIndexChanges(true);
                }
                scrollLeftPaneToPosition(changePositions.get(currentChangeIndex / 2 - 1));
                if (scrollListenerA != null) {
                    SwingUtilities.invokeLater(() -> scrollListenerA.setIgnoreIndexChanges(true));
                }
            }
        });

        nextChangeButton.addActionListener(e -> {
            if (currentChangeIndex < changePositions.size() * 2 - 1) {
                currentChangeIndex += currentChangeIndex % 2 == 0 ? 2 : 1;
                if (scrollListenerA != null) {
                    scrollListenerA.setIgnoreIndexChanges(true);
                }
                scrollLeftPaneToPosition(changePositions.get(currentChangeIndex / 2 - 1));
                if (scrollListenerA != null) {
                    SwingUtilities.invokeLater(() -> scrollListenerA.setIgnoreIndexChanges(true));
                }
            }
        });

        setContentPane(rootPanel);
        pack();
    }

    private List<LinewiseDiffItem> convertDiffItems(List<DiffItem> plainItems,
                                                    TextsLinesEncoding textsLinesEncoding) {
        List<LinewiseDiffItem> result = new ArrayList<>(plainItems.size()); // at least the same size
        LinewiseDiffItem pendingItem = null;

        for (DiffItem plainItem : plainItems) {
            String[] decodedStrings = decodeStrings(textsLinesEncoding, plainItem);
            switch (plainItem.getType()) {
                case EQUAL:
                    if (pendingItem != null) {
                        result.add(pendingItem);
                        pendingItem = null;
                    }
                    result.add(new LinewiseDiffItem(ExtendedDiffItemType.EQUAL,
                            decodedStrings,null));
                    break;

                case INSERT:
                    if (pendingItem != null) {
                        if (pendingItem.type == ExtendedDiffItemType.DELETE) {
                            // got DELETE-INSERT pair
                            result.add(new LinewiseDiffItem(ExtendedDiffItemType.MODIFIED,
                                    null, generateCharwiseItems(pendingItem.strings, decodedStrings)));
                        } else {
                            result.add(pendingItem);
                        }
                        pendingItem = null;
                    } else {
                        pendingItem = new LinewiseDiffItem(ExtendedDiffItemType.INSERT,
                                decodedStrings,null);
                    }
                    break;

                case DELETE:
                    if (pendingItem != null) {
                        if (pendingItem.type == ExtendedDiffItemType.INSERT) {
                            // got INSERT-DELETE pair
                            result.add(new LinewiseDiffItem(ExtendedDiffItemType.MODIFIED,
                                    null, generateCharwiseItems(decodedStrings, pendingItem.strings)));
                        } else {
                            result.add(pendingItem);
                        }
                        pendingItem = null;
                    } else {
                        pendingItem = new LinewiseDiffItem(ExtendedDiffItemType.DELETE,
                                decodedStrings,null);
                    }
                    break;

                default:
                    throw new RuntimeException("unexpected diff item type: " + plainItem.getType());
            }
        }

        if (pendingItem != null) {
            result.add(pendingItem);
        }

        return result;
    }

    private List<CharwiseDiffItem> generateCharwiseItems(String[] deleteLines, String[] insertLines) {
        String a = Arrays.stream(deleteLines).collect(Collectors.joining("\n"));
        String b = Arrays.stream(insertLines).collect(Collectors.joining("\n"));

        List<DiffItem> charwisePlainItems = new MyersDiffGenerator().generate(
                DiffGeneratorUtils.encodeString(a), DiffGeneratorUtils.encodeString(b));

        List<CharwiseDiffItem> result = new ArrayList<>(charwisePlainItems.size()); // at least the same size
        CharwiseDiffItem pendingItem = null;

        for (DiffItem plainItem : charwisePlainItems) {
            String decodedString = DiffGeneratorUtils.decodeString(plainItem.getChars());
            switch (plainItem.getType()) {
                case EQUAL:
                    if (pendingItem != null) {
                        result.add(pendingItem);
                        pendingItem = null;
                    }
                    result.add(new CharwiseDiffItem(ExtendedDiffItemType.EQUAL,
                            decodedString,null));
                    break;

                case INSERT:
                    if (pendingItem != null) {
                        if (pendingItem.type == ExtendedDiffItemType.DELETE) {
                            // got DELETE-INSERT pair
                            result.add(new CharwiseDiffItem(ExtendedDiffItemType.MODIFIED,
                                    pendingItem.chars, decodedString));
                        } else {
                            result.add(pendingItem);
                        }
                        pendingItem = null;
                    } else {
                        pendingItem = new CharwiseDiffItem(ExtendedDiffItemType.INSERT,
                                decodedString,null);
                    }
                    break;

                case DELETE:
                    if (pendingItem != null) {
                        if (pendingItem.type == ExtendedDiffItemType.INSERT) {
                            // got INSERT-DELETE pair
                            result.add(new CharwiseDiffItem(ExtendedDiffItemType.MODIFIED,
                                    decodedString, pendingItem.chars));
                        } else {
                            result.add(pendingItem);
                        }
                        pendingItem = null;
                    } else {
                        pendingItem = new CharwiseDiffItem(ExtendedDiffItemType.DELETE,
                                decodedString,null);
                    }
                    break;

                default:
                    throw new RuntimeException("unexpected diff item type: " + plainItem.getType());
            }
        }

        if (pendingItem != null) {
            result.add(pendingItem);
        }

        return result;
    }

    private void populateDiffAreas() throws BadLocationException {
        textAreaA = makeTextArea();
        textAreaB = makeTextArea();

        diffItemPositionsA = new ArrayList<>();
        diffItemPositionsB = new ArrayList<>();

        changePositions = new ArrayList<>();

        for (LinewiseDiffItem item : diffItems) {
            switch (item.type) {
                case EQUAL:
                    diffItemPositionsA.add(addLinesToTextArea(textAreaA, item.strings));
                    diffItemPositionsB.add(addLinesToTextArea(textAreaB, item.strings));

                    break;

                case DELETE:
                    DiffItemTextPosition positionA = addLinesToTextArea(textAreaA, item.strings);

                    int nextCharPositionB = textAreaB.getLineCount() != 0 ?
                            textAreaB.getLineEndOffset(textAreaB.getLineCount() - 1) + 1
                            : 0;

                    diffItemPositionsA.add(positionA);
                    diffItemPositionsB.add(new DiffItemTextPosition(nextCharPositionB, nextCharPositionB));

                    changePositions.add(positionA.start);

                    break;

                case INSERT:
                    DiffItemTextPosition positionB = addLinesToTextArea(textAreaB, item.strings);

                    int nextCharPositionA = textAreaA.getLineCount() != 0 ?
                            textAreaA.getLineEndOffset(textAreaA.getLineCount() - 1) + 1
                            : 0;

                    diffItemPositionsA.add(new DiffItemTextPosition(nextCharPositionA, nextCharPositionA));
                    diffItemPositionsB.add(positionB);

                    changePositions.add(nextCharPositionA);

                    break;

                case MODIFIED:
                    addModifiedLines(item);
                    break;

                default:
                    throw new RuntimeException("unexpected diff item type: " + item.type);
            }
        }

        int diffPositionIdxA = 0;
        int diffPositionIdxB = 0;

        // for some reason we can not assign highlighters as we append line to text area;
        // doing it in separate cycle
        for (LinewiseDiffItem item : diffItems) {
            DiffItemTextPosition positionA = diffItemPositionsA.get(diffPositionIdxA++);
            DiffItemTextPosition positionB = diffItemPositionsB.get(diffPositionIdxB++);

            switch (item.type) {
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

                case MODIFIED:
                    textAreaA.getHighlighter().addHighlight(positionA.start, positionA.end,
                            new WholeLineHighlightPainter(MODIFIED_LINES_HIGHLIGHT_COLOR));
                    textAreaB.getHighlighter().addHighlight(positionB.start, positionB.end,
                            new WholeLineHighlightPainter(MODIFIED_LINES_HIGHLIGHT_COLOR));

                    highlightCharwiseModifications(positionA, positionB, item);
                    break;

                default:
                    throw new RuntimeException("unexpected diff item type: " + item.type);
            }
        }

        fileAScrollPane.getViewport().setView(textAreaA);
        fileBScrollPane.getViewport().setView(textAreaB);
    }

    private void addModifiedLines(LinewiseDiffItem modifiedItem) throws BadLocationException {
        int previousLineCountA = textAreaA.getLineCount();
        if (previousLineCountA != 0) {
            textAreaA.append("\n");
        }

        int previousLineCountB = textAreaB.getLineCount();
        if (previousLineCountB != 0) {
            textAreaB.append("\n");
        }

        for (CharwiseDiffItem charwiseItem : modifiedItem.charwiseDiffItems) {
            switch (charwiseItem.type) {
                case EQUAL:
                    textAreaA.append(charwiseItem.chars);
                    textAreaB.append(charwiseItem.chars);
                    break;

                case MODIFIED:
                    textAreaA.append(charwiseItem.chars);
                    textAreaB.append(charwiseItem.otherChars);
                    break;

                case INSERT:
                    textAreaB.append(charwiseItem.chars);
                    break;

                case DELETE:
                    textAreaA.append(charwiseItem.chars);
                    break;

                default:
                    throw new RuntimeException("unexpected diff item type: " + charwiseItem.type);
            }
        }

        DiffItemTextPosition positionA = new DiffItemTextPosition(textAreaA.getLineStartOffset(previousLineCountA),
                textAreaA.getLineEndOffset(textAreaA.getLineCount() - 1));

        DiffItemTextPosition positionB = new DiffItemTextPosition(textAreaB.getLineStartOffset(previousLineCountB),
                textAreaB.getLineEndOffset(textAreaB.getLineCount() - 1));

        changePositions.add(positionA.start);

        diffItemPositionsA.add(positionA);
        diffItemPositionsB.add(positionB);
    }

    private void highlightCharwiseModifications(DiffItemTextPosition positionA, DiffItemTextPosition positionB,
                                                LinewiseDiffItem modifiedItem) throws BadLocationException {
        int posA = positionA.start;
        int posB = positionB.start;

        for (CharwiseDiffItem charwiseItem : modifiedItem.charwiseDiffItems) {
            switch (charwiseItem.type) {
                case EQUAL:
                    // no highlight required
                    posA += charwiseItem.chars.length();
                    posB += charwiseItem.chars.length();
                    break;

                case MODIFIED:
                    textAreaA.getHighlighter().addHighlight(posA, posA + charwiseItem.chars.length(),
                            new DefaultHighlighter.DefaultHighlightPainter(MODIFIED_CHARS_HIGHLIGHT_COLOR));
                    textAreaB.getHighlighter().addHighlight(posB, posB + charwiseItem.otherChars.length(),
                            new DefaultHighlighter.DefaultHighlightPainter(MODIFIED_CHARS_HIGHLIGHT_COLOR));
                    posA += charwiseItem.chars.length();
                    posB += charwiseItem.otherChars.length();
                    break;

                case INSERT:
                    // TODO add highlight on insertion point in A
                    textAreaB.getHighlighter().addHighlight(posB, posB + charwiseItem.chars.length(),
                            new DefaultHighlighter.DefaultHighlightPainter(INSERTED_LINES_HIGHLIGHT_COLOR));
                    posB += charwiseItem.chars.length();
                    break;

                case DELETE:
                    // TODO add highlight on deletion point in B
                    textAreaA.getHighlighter().addHighlight(posA, posA + charwiseItem.chars.length(),
                            new DefaultHighlighter.DefaultHighlightPainter(DELETED_LINES_HIGHLIGHT_COLOR));
                    posA += charwiseItem.chars.length();
                    break;

                default:
                    throw new RuntimeException("unexpected diff item type: " + charwiseItem.type);
            }
        }
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
        int changePositionIdx = 0;

        for (LinewiseDiffItem item : diffItems) {
            switch (item.type) {
                case EQUAL:
                case MODIFIED:
                    DiffItemTextPosition positionA = diffItemPositionsA.get(diffPositionIdxA++);
                    Rectangle firstCharRectA = textAreaA.modelToView(positionA.start);
                    Rectangle lastCharRectA = textAreaA.modelToView(positionA.end);

                    DiffItemTextPosition positionB = diffItemPositionsB.get(diffPositionIdxB++);
                    Rectangle firstCharRectB = textAreaB.modelToView(positionB.start);
                    Rectangle lastCharRectB = textAreaB.modelToView(positionB.end);

                    if (item.type == ExtendedDiffItemType.EQUAL) {
                        changePositionIdx += 1;
                    } else {
                        changePositionIdx += changePositionIdx % 2 == 0 ? 2 : 1;
                    }

                    scrollRangesA.add(new BoundScrollRange(
                            firstCharRectA.getLocation().y,
                            lastCharRectA.getLocation().y + lastCharRectA.height,
                            firstCharRectB.getLocation().y,
                            true,
                            changePositionIdx));

                    scrollRangesB.add(new BoundScrollRange(
                            firstCharRectB.getLocation().y,
                            lastCharRectB.getLocation().y + lastCharRectB.height,
                            firstCharRectA.getLocation().y,
                            true,
                            changePositionIdx));

                    break;

                case DELETE:
                    positionA = diffItemPositionsA.get(diffPositionIdxA++);
                    firstCharRectA = textAreaA.modelToView(positionA.start);
                    lastCharRectA = textAreaA.modelToView(positionA.end);

                    positionB = diffItemPositionsB.get(diffPositionIdxB++);
                    firstCharRectB = textAreaB.modelToView(positionB.start);

                    changePositionIdx += changePositionIdx % 2 == 0 ? 2 : 1;

                    scrollRangesA.add(new BoundScrollRange(
                            firstCharRectA.getLocation().y,
                            lastCharRectA.getLocation().y + lastCharRectA.height,
                            firstCharRectB.getLocation().y,
                            false,
                            changePositionIdx));

                    break;

                case INSERT:
                    positionB = diffItemPositionsB.get(diffPositionIdxB++);
                    firstCharRectB = textAreaB.modelToView(positionB.start);
                    lastCharRectB = textAreaB.modelToView(positionB.end);

                    positionA = diffItemPositionsA.get(diffPositionIdxA++);
                    firstCharRectA = textAreaA.modelToView(positionA.start);

                    changePositionIdx += changePositionIdx % 2 == 0 ? 2 : 1;

                    scrollRangesB.add(new BoundScrollRange(
                            firstCharRectB.getLocation().y,
                            lastCharRectB.getLocation().y + lastCharRectB.height,
                            firstCharRectA.getLocation().y,
                            false,
                            changePositionIdx));

                    break;

                default:
                    throw new RuntimeException("unexpected diff item type: " + item.type);
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
            if ((pos >= r.startThis) && (pos < r.endThis)) {
                return r;
            }
        }
        return null; // not found
    }

    private Point getViewportCenterPosition(JScrollPane scrollPane) {
        JViewport viewport = scrollPane.getViewport();
        Point topViewPosition = viewport.getViewPosition();
        return new Point(topViewPosition.x, topViewPosition.y + viewport.getHeight() / 2);
    }

    private void setViewportCenterPosition(JScrollPane scrollPane, Point centerPoint) {
        JViewport viewport = scrollPane.getViewport();
        viewport.setViewPosition(new Point(centerPoint.x, Math.max(0, centerPoint.y - viewport.getHeight() / 2)));
        scrollPane.repaint();
    }

    private void scrollLeftPaneToPosition(int position) {
        Rectangle rect;
        try {
            rect = textAreaA.modelToView(position);
        } catch (BadLocationException e) {
            throw new RuntimeException(e);
        }
        setViewportCenterPosition(fileAScrollPane, new Point(rect.x, rect.y));
    }
}

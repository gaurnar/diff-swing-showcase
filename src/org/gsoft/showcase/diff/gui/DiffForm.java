package org.gsoft.showcase.diff.gui;

import org.gsoft.showcase.diff.generators.DiffGeneratorUtils;
import org.gsoft.showcase.diff.generators.DiffGeneratorUtils.LinesEncoding;
import org.gsoft.showcase.diff.generators.DiffItem;
import org.gsoft.showcase.diff.generators.DiffItemType;
import org.gsoft.showcase.diff.generators.impl.MyersDiffGenerator;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class DiffForm extends JFrame {
    public static final Color DELETED_LINES_HIGHLIGHT_COLOR = new Color(250, 180, 170);
    public static final Color INSERTED_LINES_HIGHLIGHT_COLOR = new Color(174, 255, 202);
    public static final Color MODIFIED_LINES_HIGHLIGHT_COLOR = new Color(221, 226, 255);
    public static final Color MODIFIED_CHARS_HIGHLIGHT_COLOR = new Color(187, 211, 255);

    private static class TextPosition {
        final int start, end;

        TextPosition(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    /**
     * Extends {@link DiffItemType} with MODIFIED item type.
     */
    public enum ExtendedDiffItemType {
        EQUAL,
        INSERT,
        DELETE,
        MODIFIED
    }

    /**
     * TODO convert to type hierarchy
     */
    private static final class ByLineDiffItem {
        final ExtendedDiffItemType type;
        final String[] strings;
        final List<DiffItem> byCharDiffItems;

        private ByLineDiffItem(ExtendedDiffItemType type, String[] strings,
                               List<DiffItem> byCharDiffItems) {
            this.type = type;
            this.strings = strings;
            this.byCharDiffItems = byCharDiffItems != null ? new ArrayList<>(byCharDiffItems) : null;
        }
    }

    private final List<ByLineDiffItem> diffItems;

    private JPanel rootPanel;
    private JLabel fileAPathLabel;
    private JLabel fileBPathLabel;
    private JScrollPane fileAScrollPane;
    private JScrollPane fileBScrollPane;
    private JButton prevChangeButton;
    private JButton nextChangeButton;
    private JPanel diffMatchingWrapperPanel;

    private JTextArea textAreaA;
    private JTextArea textAreaB;

    public DiffForm(String fileAPath, String fileBPath,
                    List<DiffItem> byLineDiffItems,
                    LinesEncoding linesEncoding) {
        setTitle("Diff");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(800, 600));

        fileAPathLabel.setText(fileAPath);
        fileBPathLabel.setText(fileBPath);

        this.diffItems = convertByLineDiffItems(byLineDiffItems, linesEncoding);

        List<DiffPanesScrollController.DiffItemPosition> diffItemPositions;

        try {
            diffItemPositions = populateDiffAreas();
        } catch (BadLocationException e) {
            throw new RuntimeException(e);
        }

        DiffMatchingImagePanel diffMatchingImagePanel = new DiffMatchingImagePanel();

        diffMatchingWrapperPanel.add(diffMatchingImagePanel);

        DiffPanesScrollController scrollController = new DiffPanesScrollController(
                fileAScrollPane, fileBScrollPane, this, diffMatchingImagePanel,
                diffItemPositions);

        prevChangeButton.setMnemonic(KeyEvent.VK_LEFT);
        prevChangeButton.setToolTipText("Previous change " + getMnemonicKeyHint("Left"));
        prevChangeButton.addActionListener(e -> scrollController.scrollToPreviousChange());

        nextChangeButton.setMnemonic(KeyEvent.VK_RIGHT);
        nextChangeButton.setToolTipText("Next change " + getMnemonicKeyHint("Right"));
        nextChangeButton.addActionListener(e -> scrollController.scrollToNextChange());

        // TODO can it be done in designer?
        prevChangeButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        nextChangeButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        setContentPane(rootPanel);
        pack();
    }

    private List<ByLineDiffItem> convertByLineDiffItems(List<DiffItem> plainItems,
                                                        LinesEncoding linesEncoding) {
        List<ByLineDiffItem> result = new ArrayList<>(plainItems.size()); // at least the same size
        ByLineDiffItem pendingInsertOrDelete = null;

        //
        // applying heuristic to improve diff display: converting consecutive
        // INSERT-DELETE or DELETE-INSERT into new diff element - MODIFIED
        //
        for (DiffItem plainItem : plainItems) {
            String[] decodedStrings = decodeStrings(linesEncoding, plainItem);
            switch (plainItem.getType()) {
                case EQUAL:
                    if (pendingInsertOrDelete != null) {
                        result.add(pendingInsertOrDelete);
                        pendingInsertOrDelete = null;
                    }
                    result.add(new ByLineDiffItem(ExtendedDiffItemType.EQUAL,
                            decodedStrings,null));
                    break;

                case INSERT:
                    ByLineDiffItem newInsert = new ByLineDiffItem(ExtendedDiffItemType.INSERT,
                            decodedStrings,null);
                    if (pendingInsertOrDelete != null) {
                        result.add(createModifiedItem(pendingInsertOrDelete, newInsert));
                        pendingInsertOrDelete = null;
                    } else {
                        pendingInsertOrDelete = newInsert;
                    }
                    break;

                case DELETE:
                    ByLineDiffItem newDelete = new ByLineDiffItem(ExtendedDiffItemType.DELETE,
                            decodedStrings,null);
                    if (pendingInsertOrDelete != null) {
                        result.add(createModifiedItem(pendingInsertOrDelete, newDelete));
                        pendingInsertOrDelete = null;
                    } else {
                        pendingInsertOrDelete = newDelete;
                    }
                    break;

                default:
                    throw new RuntimeException("unexpected diff item type: " + plainItem.getType());
            }
        }

        if (pendingInsertOrDelete != null) {
            result.add(pendingInsertOrDelete);
        }

        return result;
    }

    private ByLineDiffItem createModifiedItem(ByLineDiffItem firstItem, ByLineDiffItem secondItem) {
        ByLineDiffItem deleteItem, insertItem;
        if ((firstItem.type == ExtendedDiffItemType.DELETE) && (secondItem.type == ExtendedDiffItemType.INSERT)) {
            deleteItem = firstItem;
            insertItem = secondItem;
        } else if ((firstItem.type == ExtendedDiffItemType.INSERT) && (secondItem.type == ExtendedDiffItemType.DELETE)) {
            deleteItem = secondItem;
            insertItem = firstItem;
        } else {
            throw new IllegalArgumentException("items must only be inserts or deletes!");
        }

        List<DiffItem> byCharPlainItems = produceByCharDiff(deleteItem.strings, insertItem.strings);

        return new ByLineDiffItem(ExtendedDiffItemType.MODIFIED, null, byCharPlainItems);
    }

    private List<DiffItem> produceByCharDiff(String[] stringsA, String[] stringsB) {
        String a = Arrays.stream(stringsA).collect(Collectors.joining("\n"));
        String b = Arrays.stream(stringsB).collect(Collectors.joining("\n"));

        return new MyersDiffGenerator().generate(
                DiffGeneratorUtils.encodeString(a), DiffGeneratorUtils.encodeString(b));
    }

    private List<DiffPanesScrollController.DiffItemPosition> populateDiffAreas() throws BadLocationException {
        textAreaA = makeTextArea();
        textAreaB = makeTextArea();

        List<DiffPanesScrollController.DiffItemPosition> diffItemPositions = new ArrayList<>();

        for (ByLineDiffItem item : diffItems) {
            switch (item.type) {
                case EQUAL:
                    TextPosition positionA = addLinesToTextArea(textAreaA, item.strings);
                    TextPosition positionB = addLinesToTextArea(textAreaB, item.strings);

                    diffItemPositions.add(new DiffPanesScrollController.DiffItemPosition(
                            positionA.start, positionB.start, positionA.end, positionB.end, item.type));

                    break;

                case DELETE:
                    positionA = addLinesToTextArea(textAreaA, item.strings);

                    int nextCharPositionB = textAreaB.getLineCount() != 0 ?
                            textAreaB.getLineEndOffset(textAreaB.getLineCount() - 1) + 1
                            : 0;

                    diffItemPositions.add(new DiffPanesScrollController.DiffItemPosition(
                            positionA.start, nextCharPositionB, positionA.end, nextCharPositionB, item.type));

                    break;

                case INSERT:
                    positionB = addLinesToTextArea(textAreaB, item.strings);

                    int nextCharPositionA = textAreaA.getLineCount() != 0 ?
                            textAreaA.getLineEndOffset(textAreaA.getLineCount() - 1) + 1
                            : 0;

                    diffItemPositions.add(new DiffPanesScrollController.DiffItemPosition(
                            nextCharPositionA, positionB.start, nextCharPositionA, positionB.end, item.type));

                    break;

                case MODIFIED:
                    diffItemPositions.add(addModifiedLines(item));
                    break;

                default:
                    throw new RuntimeException("unexpected diff item type: " + item.type);
            }
        }

        //
        // we can not assign highlighters as we append line to text area;
        // doing it in a separate pass
        //
        for (int i = 0; i < diffItems.size(); i++) {
            DiffPanesScrollController.DiffItemPosition itemPos = diffItemPositions.get(i);
            switch (itemPos.getType()) {
                case EQUAL:
                    // no highlight necessary
                    break;

                case DELETE:
                    highlightByLineDiffItem(itemPos,
                            new WholeLineHighlightPainter(DELETED_LINES_HIGHLIGHT_COLOR),
                            new InsertOrDeletePointHighlighter(DELETED_LINES_HIGHLIGHT_COLOR));
                    break;

                case INSERT:
                    highlightByLineDiffItem(itemPos,
                            new InsertOrDeletePointHighlighter(INSERTED_LINES_HIGHLIGHT_COLOR),
                            new WholeLineHighlightPainter(INSERTED_LINES_HIGHLIGHT_COLOR));
                    break;

                case MODIFIED:
                    highlightByLineDiffItem(itemPos,
                            new WholeLineHighlightPainter(MODIFIED_LINES_HIGHLIGHT_COLOR),
                            new WholeLineHighlightPainter(MODIFIED_LINES_HIGHLIGHT_COLOR));
                    highlightByCharModifications(itemPos, diffItems.get(i));
                    break;

                default:
                    throw new RuntimeException("unexpected diff item type: " + itemPos.getType());
            }
        }

        fileAScrollPane.getViewport().setView(textAreaA);
        fileBScrollPane.getViewport().setView(textAreaB);

        return diffItemPositions;
    }

    private void highlightByLineDiffItem(DiffPanesScrollController.DiffItemPosition item,
                                         Highlighter.HighlightPainter highlightPainterA,
                                         Highlighter.HighlightPainter highlightPainterB)
            throws BadLocationException {
        textAreaA.getHighlighter().addHighlight(item.getStartA(), item.getEndA(),
                highlightPainterA);
        textAreaB.getHighlighter().addHighlight(item.getStartB(), item.getEndB(),
                highlightPainterB);
    }

    private DiffPanesScrollController.DiffItemPosition addModifiedLines(ByLineDiffItem modifiedItem)
            throws BadLocationException {
        int previousLineCountA = textAreaA.getLineCount();
        if (previousLineCountA != 1) {
            textAreaA.append("\n");
        }

        int previousLineCountB = textAreaB.getLineCount();
        if (previousLineCountB != 1) {
            textAreaB.append("\n");
        }

        for (DiffItem byCharItem : modifiedItem.byCharDiffItems) {
            String decodedString = DiffGeneratorUtils.decodeString(byCharItem.getChars());
            switch (byCharItem.getType()) {
                case EQUAL:
                    textAreaA.append(decodedString);
                    textAreaB.append(decodedString);
                    break;

                case INSERT:
                    textAreaB.append(decodedString);
                    break;

                case DELETE:
                    textAreaA.append(decodedString);
                    break;

                default:
                    throw new RuntimeException("unexpected diff item type: " + byCharItem.getType());
            }
        }

        TextPosition positionA = new TextPosition(textAreaA.getLineStartOffset(previousLineCountA),
                textAreaA.getLineEndOffset(textAreaA.getLineCount() - 1));

        TextPosition positionB = new TextPosition(textAreaB.getLineStartOffset(previousLineCountB),
                textAreaB.getLineEndOffset(textAreaB.getLineCount() - 1));

        return new DiffPanesScrollController.DiffItemPosition(
                positionA.start, positionB.start, positionA.end, positionB.end,
                ExtendedDiffItemType.MODIFIED);
    }

    private void highlightByCharModifications(DiffPanesScrollController.DiffItemPosition position,
                                              ByLineDiffItem modifiedItem) throws BadLocationException {
        int posB = position.getStartB();

        //
        // applying simple heuristic to improve readability: coalescing highlight of
        // small EQUAL items (of 3 chars or less) with neighbouring INSERT items
        //
        int[] pendingHighlightPositions = null;

        for (DiffItem byCharItem : modifiedItem.byCharDiffItems) {
            switch (byCharItem.getType()) {
                case EQUAL:
                    posB += byCharItem.getChars().length;
                    if (pendingHighlightPositions != null) {
                        if (byCharItem.getChars().length <= 3) {
                            pendingHighlightPositions[1] = posB;
                        } else {
                            highlightByCharModifications(pendingHighlightPositions[0], pendingHighlightPositions[1]);
                            pendingHighlightPositions = null;
                        }
                    }
                    break;

                case INSERT:
                    if (pendingHighlightPositions != null) {
                        pendingHighlightPositions[1] = posB + byCharItem.getChars().length;
                    } else {
                        pendingHighlightPositions = new int[] {posB, posB + byCharItem.getChars().length};
                    }
                    posB += byCharItem.getChars().length;
                    break;

                case DELETE:
                    // ignoring
                    break;

                default:
                    throw new RuntimeException("unexpected diff item type: " + byCharItem.getType());
            }
        }

        if (pendingHighlightPositions != null) {
            highlightByCharModifications(pendingHighlightPositions[0], pendingHighlightPositions[1]);
        }
    }

    private void highlightByCharModifications(int start, int end) throws BadLocationException {
        textAreaB.getHighlighter().addHighlight(start, end,
                new DefaultHighlighter.DefaultHighlightPainter(MODIFIED_CHARS_HIGHLIGHT_COLOR));
    }

    private static TextPosition addLinesToTextArea(JTextArea textArea, String[] lines) throws BadLocationException {
        int previousLineCount = textArea.getLineCount();
        if (previousLineCount != 1) {
            textArea.append("\n");
        }
        textArea.append(Arrays.stream(lines).collect(Collectors.joining("\n")));
        return new TextPosition(textArea.getLineStartOffset(previousLineCount),
                textArea.getLineEndOffset(textArea.getLineCount() - 1));
    }

    private static String[] decodeStrings(LinesEncoding linesEncoding, DiffItem item) {
        return DiffGeneratorUtils.decodeLines(item.getChars(), linesEncoding);
    }

    private JTextArea makeTextArea() {
        JTextArea textArea = new JTextArea();
        textArea.setFont(new Font("Courier New", Font.PLAIN, 11));
        textArea.setEditable(false); // TODO
        textArea.setLineWrap(false);
        textArea.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
        return textArea;
    }

    private static String getMnemonicKeyHint(String keyDisplayName) {
        String mnemonicPrefix;

        String osName = System.getProperty("os.name").toLowerCase();

        if (osName.contains("mac ")) {
            mnemonicPrefix = "Ctrl+Alt+"; // TODO is this universal?
        } else {
            mnemonicPrefix = "Alt+";
        }

        return "(" + mnemonicPrefix + keyDisplayName + ")";
    }
}

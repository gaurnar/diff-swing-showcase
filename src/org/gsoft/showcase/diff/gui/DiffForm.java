package org.gsoft.showcase.diff.gui;

import org.gsoft.showcase.diff.generators.DiffGenerator;
import org.gsoft.showcase.diff.generators.DiffGenerator.DiffItem;
import org.gsoft.showcase.diff.generators.DiffGeneratorUtils;
import org.gsoft.showcase.diff.generators.DiffGeneratorUtils.TextsLinesEncoding;
import org.gsoft.showcase.diff.generators.impl.MyersDiffGenerator;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
     * TODO convert to type hierarchy
     */
    private static final class ByCharDiffItem {
        final ExtendedDiffItemType type;
        final String chars;
        final String modifiedChars;

        ByCharDiffItem(ExtendedDiffItemType type, String chars, String modifiedChars) {
            this.type = type;
            this.chars = chars;
            this.modifiedChars = modifiedChars;
        }
    }

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
        final List<ByCharDiffItem> byCharDiffItems;

        private ByLineDiffItem(ExtendedDiffItemType type, String[] strings,
                               List<ByCharDiffItem> byCharDiffItems) {
            this.type = type;
            this.strings = strings;
            this.byCharDiffItems = byCharDiffItems;
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
                    TextsLinesEncoding textsLinesEncoding) {
        setTitle("Diff");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(800, 600));

        fileAPathLabel.setText(fileAPath);
        fileBPathLabel.setText(fileBPath);

        this.diffItems = convertByLineDiffItems(byLineDiffItems, textsLinesEncoding);

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
                                                        TextsLinesEncoding textsLinesEncoding) {
        List<ByLineDiffItem> result = new ArrayList<>(plainItems.size()); // at least the same size
        ByLineDiffItem pendingInsertOrDelete = null;

        //
        // applying heuristic to improve diff display: converting consecutive
        // INSERT-DELETE or DELETE-INSERT into new diff element - MODIFIED
        //
        for (DiffItem plainItem : plainItems) {
            String[] decodedStrings = decodeStrings(textsLinesEncoding, plainItem);
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
                        result.addAll(tryProduceModifiedItem(pendingInsertOrDelete, newInsert));
                        pendingInsertOrDelete = null;
                    } else {
                        pendingInsertOrDelete = newInsert;
                    }
                    break;

                case DELETE:
                    ByLineDiffItem newDelete = new ByLineDiffItem(ExtendedDiffItemType.DELETE,
                            decodedStrings,null);
                    if (pendingInsertOrDelete != null) {
                        result.addAll(tryProduceModifiedItem(pendingInsertOrDelete, newDelete));
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

    private List<ByLineDiffItem> tryProduceModifiedItem(ByLineDiffItem firstItem, ByLineDiffItem secondItem) {
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

        //
        // Using simple heuristic: counting EQUAL items with single char. If there are many of these,
        // then strings are too different to look good as MODIFIED item; we let them be a DELETE-INSERT pair.
        //
        int totalChars = 0;
        int singleEqualChars = 0;

        for (DiffItem item : byCharPlainItems) {
            if ((item.getType() == DiffGenerator.ItemType.EQUAL) && (item.getChars().length == 1)) {
                singleEqualChars++;
            }
            totalChars += item.getChars().length;
        }

        // TODO work on magic constants
        if ((singleEqualChars > 3) && // for small strings
                ((float) singleEqualChars / totalChars >= 0.03)) {
            // too many single char EQUAL items; returning DELETE-INSERT pair
            return Arrays.asList(firstItem, secondItem);
        }

        return Collections.singletonList(new ByLineDiffItem(ExtendedDiffItemType.MODIFIED, null,
                convertByCharDiffItems(byCharPlainItems)));
    }

    private List<DiffItem> produceByCharDiff(String[] stringsA, String[] stringsB) {
        String a = Arrays.stream(stringsA).collect(Collectors.joining("\n"));
        String b = Arrays.stream(stringsB).collect(Collectors.joining("\n"));

        return new MyersDiffGenerator().generate(
                DiffGeneratorUtils.encodeString(a), DiffGeneratorUtils.encodeString(b));
    }

    private List<ByCharDiffItem> convertByCharDiffItems(List<DiffItem> byCharPlainItems) {
        List<ByCharDiffItem> byCharDiffItems = new ArrayList<>(byCharPlainItems.size()); // at least the same size
        ByCharDiffItem pendingInsertOrDelete = null;

        //
        // converting consecutive INSERT-DELETE or DELETE-INSERT into MODIFIED items
        // (similar to by line diff items)
        //
        for (DiffItem plainItem : byCharPlainItems) {
            String decodedString = DiffGeneratorUtils.decodeString(plainItem.getChars());
            switch (plainItem.getType()) {
                case EQUAL:
                    if (pendingInsertOrDelete != null) {
                        byCharDiffItems.add(pendingInsertOrDelete);
                        pendingInsertOrDelete = null;
                    }
                    byCharDiffItems.add(new ByCharDiffItem(ExtendedDiffItemType.EQUAL,
                            decodedString,null));
                    break;

                case INSERT:
                    if (pendingInsertOrDelete != null) {
                        if (pendingInsertOrDelete.type != ExtendedDiffItemType.DELETE) {
                            throw new IllegalArgumentException("invalid diff items sequence: consecutive inserts");
                        }
                        // got DELETE-INSERT pair
                        byCharDiffItems.add(new ByCharDiffItem(ExtendedDiffItemType.MODIFIED,
                                pendingInsertOrDelete.chars, decodedString));
                        pendingInsertOrDelete = null;
                    } else {
                        pendingInsertOrDelete = new ByCharDiffItem(ExtendedDiffItemType.INSERT,
                                decodedString,null);
                    }
                    break;

                case DELETE:
                    if (pendingInsertOrDelete != null) {
                        if (pendingInsertOrDelete.type != ExtendedDiffItemType.INSERT) {
                            throw new IllegalArgumentException("invalid diff items sequence: consecutive deletes");
                        }
                        // got INSERT-DELETE pair
                        byCharDiffItems.add(new ByCharDiffItem(ExtendedDiffItemType.MODIFIED,
                                decodedString, pendingInsertOrDelete.chars));
                        pendingInsertOrDelete = null;
                    } else {
                        pendingInsertOrDelete = new ByCharDiffItem(ExtendedDiffItemType.DELETE,
                                decodedString,null);
                    }
                    break;

                default:
                    throw new RuntimeException("unexpected diff item type: " + plainItem.getType());
            }
        }

        if (pendingInsertOrDelete != null) {
            byCharDiffItems.add(pendingInsertOrDelete);
        }
        return byCharDiffItems;
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
        if (previousLineCountA != 0) {
            textAreaA.append("\n");
        }

        int previousLineCountB = textAreaB.getLineCount();
        if (previousLineCountB != 0) {
            textAreaB.append("\n");
        }

        for (ByCharDiffItem byCharItem : modifiedItem.byCharDiffItems) {
            switch (byCharItem.type) {
                case EQUAL:
                    textAreaA.append(byCharItem.chars);
                    textAreaB.append(byCharItem.chars);
                    break;

                case MODIFIED:
                    textAreaA.append(byCharItem.chars);
                    textAreaB.append(byCharItem.modifiedChars);
                    break;

                case INSERT:
                    textAreaB.append(byCharItem.chars);
                    break;

                case DELETE:
                    textAreaA.append(byCharItem.chars);
                    break;

                default:
                    throw new RuntimeException("unexpected diff item type: " + byCharItem.type);
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
        int posA = position.getStartA();
        int posB = position.getStartB();

        for (ByCharDiffItem byCharItem : modifiedItem.byCharDiffItems) {
            switch (byCharItem.type) {
                case EQUAL:
                    // no highlight required
                    posA += byCharItem.chars.length();
                    posB += byCharItem.chars.length();
                    break;

                case MODIFIED:
                    textAreaA.getHighlighter().addHighlight(posA, posA + byCharItem.chars.length(),
                            new DefaultHighlighter.DefaultHighlightPainter(MODIFIED_CHARS_HIGHLIGHT_COLOR));
                    textAreaB.getHighlighter().addHighlight(posB, posB + byCharItem.modifiedChars.length(),
                            new DefaultHighlighter.DefaultHighlightPainter(MODIFIED_CHARS_HIGHLIGHT_COLOR));
                    posA += byCharItem.chars.length();
                    posB += byCharItem.modifiedChars.length();
                    break;

                case INSERT:
                    // TODO add highlight on insertion point in A
                    textAreaB.getHighlighter().addHighlight(posB, posB + byCharItem.chars.length(),
                            new DefaultHighlighter.DefaultHighlightPainter(INSERTED_LINES_HIGHLIGHT_COLOR));
                    posB += byCharItem.chars.length();
                    break;

                case DELETE:
                    // TODO add highlight on deletion point in B
                    textAreaA.getHighlighter().addHighlight(posA, posA + byCharItem.chars.length(),
                            new DefaultHighlighter.DefaultHighlightPainter(DELETED_LINES_HIGHLIGHT_COLOR));
                    posA += byCharItem.chars.length();
                    break;

                default:
                    throw new RuntimeException("unexpected diff item type: " + byCharItem.type);
            }
        }
    }

    private static TextPosition addLinesToTextArea(JTextArea textArea, String[] lines) throws BadLocationException {
        int previousLineCount = textArea.getLineCount();
        if (previousLineCount != 0) {
            textArea.append("\n");
        }
        textArea.append(Arrays.stream(lines).collect(Collectors.joining("\n")));
        return new TextPosition(textArea.getLineStartOffset(previousLineCount),
                textArea.getLineEndOffset(textArea.getLineCount() - 1));
    }

    private static String[] decodeStrings(TextsLinesEncoding textsLinesEncoding, DiffItem item) {
        return DiffGeneratorUtils.decodeText(item.getChars(), textsLinesEncoding);
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
            mnemonicPrefix = "Alt+"; // TODO check it
        }

        return "(" + mnemonicPrefix + keyDisplayName + ")";
    }
}

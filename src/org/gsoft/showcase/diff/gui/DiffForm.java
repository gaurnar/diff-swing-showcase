package org.gsoft.showcase.diff.gui;

import org.gsoft.showcase.diff.logic.DiffGenerator.DiffItem;
import org.gsoft.showcase.diff.logic.DiffGeneratorUtils;
import org.gsoft.showcase.diff.logic.DiffGeneratorUtils.TextsLinesEncoding;
import org.gsoft.showcase.diff.logic.MyersDiffGenerator;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class DiffForm extends JFrame {
    private static final Color DELETED_LINES_HIGHLIGHT_COLOR = new Color(250, 180, 170);
    private static final Color INSERTED_LINES_HIGHLIGHT_COLOR = new Color(174, 255, 202);
    private static final Color MODIFIED_LINES_HIGHLIGHT_COLOR = new Color(221, 239, 255);
    private static final Color MODIFIED_CHARS_HIGHLIGHT_COLOR = new Color(187, 211, 255);

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

    public enum ExtendedDiffItemType {
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

    private JTextArea textAreaA;
    private JTextArea textAreaB;

    public DiffForm(String fileAPath, String fileBPath,
                    List<DiffItem> diffItems,
                    TextsLinesEncoding textsLinesEncoding) {
        setTitle("Diff");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(800, 600));

        fileAPathLabel.setText(fileAPath);
        fileBPathLabel.setText(fileBPath);

        this.diffItems = convertDiffItems(diffItems, textsLinesEncoding);

        List<DiffPanesScrollController.DiffItemPosition> diffItemPositions;

        try {
            diffItemPositions = populateDiffAreas();
        } catch (BadLocationException e) {
            throw new RuntimeException(e);
        }

        DiffPanesScrollController scrollController = new DiffPanesScrollController(
                fileAScrollPane, fileBScrollPane, this, diffItemPositions);

        prevChangeButton.addActionListener(e -> scrollController.scrollToPreviousChange());
        nextChangeButton.addActionListener(e -> scrollController.scrollToNextChange());

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

    private List<DiffPanesScrollController.DiffItemPosition> populateDiffAreas() throws BadLocationException {
        textAreaA = makeTextArea();
        textAreaB = makeTextArea();

        List<DiffPanesScrollController.DiffItemPosition> diffItemPositions = new ArrayList<>();

        for (LinewiseDiffItem item : diffItems) {
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

        // for some reason we can not assign highlighters as we append line to text area;
        // doing it in separate cycle
        for (int i = 0; i < diffItems.size(); i++) {
            DiffPanesScrollController.DiffItemPosition itemPos = diffItemPositions.get(i);
            switch (itemPos.getType()) {
                case EQUAL:
                    // no highlighter necessary
                    break;

                case DELETE:
                    highlightLinewiseDiffItem(itemPos,
                            new WholeLineHighlightPainter(DELETED_LINES_HIGHLIGHT_COLOR),
                            new InsertOrDeletePointHighlighter(DELETED_LINES_HIGHLIGHT_COLOR));
                    break;

                case INSERT:
                    highlightLinewiseDiffItem(itemPos,
                            new InsertOrDeletePointHighlighter(INSERTED_LINES_HIGHLIGHT_COLOR),
                            new WholeLineHighlightPainter(INSERTED_LINES_HIGHLIGHT_COLOR));
                    break;

                case MODIFIED:
                    highlightLinewiseDiffItem(itemPos,
                            new WholeLineHighlightPainter(MODIFIED_LINES_HIGHLIGHT_COLOR),
                            new WholeLineHighlightPainter(MODIFIED_LINES_HIGHLIGHT_COLOR));
                    highlightCharwiseModifications(itemPos, diffItems.get(i));
                    break;

                default:
                    throw new RuntimeException("unexpected diff item type: " + itemPos.getType());
            }
        }

        fileAScrollPane.getViewport().setView(textAreaA);
        fileBScrollPane.getViewport().setView(textAreaB);

        return diffItemPositions;
    }

    private void highlightLinewiseDiffItem(DiffPanesScrollController.DiffItemPosition item,
                                           Highlighter.HighlightPainter highlightPainterA,
                                           Highlighter.HighlightPainter highlightPainterB)
            throws BadLocationException {
        textAreaA.getHighlighter().addHighlight(item.getStartA(), item.getEndA(),
                highlightPainterA);
        textAreaB.getHighlighter().addHighlight(item.getStartB(), item.getEndB(),
                highlightPainterB);
    }

    private DiffPanesScrollController.DiffItemPosition addModifiedLines(LinewiseDiffItem modifiedItem)
            throws BadLocationException {
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

        TextPosition positionA = new TextPosition(textAreaA.getLineStartOffset(previousLineCountA),
                textAreaA.getLineEndOffset(textAreaA.getLineCount() - 1));

        TextPosition positionB = new TextPosition(textAreaB.getLineStartOffset(previousLineCountB),
                textAreaB.getLineEndOffset(textAreaB.getLineCount() - 1));

        return new DiffPanesScrollController.DiffItemPosition(
                positionA.start, positionB.start, positionA.end, positionB.end,
                ExtendedDiffItemType.MODIFIED);
    }

    private void highlightCharwiseModifications(DiffPanesScrollController.DiffItemPosition position,
                                                LinewiseDiffItem modifiedItem) throws BadLocationException {
        int posA = position.getStartA();
        int posB = position.getStartB();

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
}

package org.gsoft.showcase.diff.gui;

import org.gsoft.showcase.diff.logic.DiffGenerator.DiffItem;
import org.gsoft.showcase.diff.logic.DiffGeneratorUtils;
import org.gsoft.showcase.diff.logic.DiffGeneratorUtils.TextsLinesEncoding;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class DiffForm extends JFrame {
    private static final Color DELETED_LINES_BACKGROUND = new Color(250, 180, 170);
    private static final Color INSERTED_LINES_BACKGROUND = new Color(174, 255, 202);

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

        private BoundScrollRange currentScrollRange;

        public ScrollListener(JScrollPane thisScrollPane,
                              JScrollPane otherScrollPane,
                              List<BoundScrollRange> scrollRanges) {
            this.thisScrollPane = thisScrollPane;
            this.otherScrollPane = otherScrollPane;
            this.scrollRanges = scrollRanges;
        }

        @Override
        public void stateChanged(ChangeEvent e) {
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
            setViewportCenterPosition(otherScrollPane, new Point(otherPosition.x,
                    currentScrollRange.scrollOther ?
                            currentScrollRange.startOther + thisCenterPosition.y - currentScrollRange.startThis
                            : currentScrollRange.startOther
            ));
        }

        private static Point getViewportCenterPosition(JScrollPane scrollPane) {
            JViewport viewport = scrollPane.getViewport();
            Point topViewPosition = viewport.getViewPosition();
            return new Point(topViewPosition.x, topViewPosition.y + viewport.getHeight() / 2);
        }

        private static void setViewportCenterPosition(JScrollPane scrollPane, Point centerPoint) {
            JViewport viewport = scrollPane.getViewport();
            viewport.setViewPosition(new Point(centerPoint.x, centerPoint.y - viewport.getHeight() / 2));
        }
    }

    private final List<DiffItem> diffItems;

    private JPanel rootPanel;
    private JLabel fileAPathLabel;
    private JLabel fileBPathLabel;
    private JScrollPane fileAScrollPane;
    private JScrollPane fileBScrollPane;

    private List<JComponent> textComponentsA;
    private List<JComponent> textComponentsB;

    private ScrollListener scrollListenerA;
    private ScrollListener scrollListenerB;

    public DiffForm(String fileAPath, String fileBPath,
                    List<DiffItem> diffItems,
                    TextsLinesEncoding textsLinesEncoding) {
        this.diffItems = diffItems;

        setTitle("Diff");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(800, 600));

        fileAPathLabel.setText(fileAPath);
        fileBPathLabel.setText(fileBPath);

        populateDiffPanes(textsLinesEncoding);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                calculateSideBySideScrolling();
            }
        });

        setContentPane(rootPanel);
        pack();
    }

    private void populateDiffPanes(TextsLinesEncoding textsLinesEncoding) {
        JPanel wrapperPanelA = new JPanel();
        JPanel wrapperPanelB = new JPanel();

        wrapperPanelA.setBackground(Color.WHITE);
        wrapperPanelB.setBackground(Color.WHITE);

        wrapperPanelA.setLayout(new BoxLayout(wrapperPanelA, BoxLayout.PAGE_AXIS));
        wrapperPanelB.setLayout(new BoxLayout(wrapperPanelB, BoxLayout.PAGE_AXIS));

        textComponentsA = new ArrayList<>();
        textComponentsB = new ArrayList<>();

        for (DiffItem item : diffItems) {
            JComponent textComponent;
            switch (item.getType()) {
                case EQUAL:
                    String decodedString = decodeStrings(textsLinesEncoding, item);
                    JComponent textComponentA = makeTextComponent(decodedString, Color.WHITE);
                    JComponent textComponentB = makeTextComponent(decodedString, Color.WHITE);

                    wrapperPanelA.add(textComponentA);
                    textComponentsA.add(textComponentA);

                    wrapperPanelB.add(textComponentB);
                    textComponentsB.add(textComponentB);

                    break;

                case DELETE:
                    textComponent = makeTextComponent(decodeStrings(textsLinesEncoding, item),
                            DELETED_LINES_BACKGROUND);

                    wrapperPanelA.add(textComponent);
                    textComponentsA.add(textComponent);

                    wrapperPanelB.add(makeSeparator(DELETED_LINES_BACKGROUND));

                    break;

                case INSERT:
                    textComponent = makeTextComponent(decodeStrings(textsLinesEncoding, item),
                            INSERTED_LINES_BACKGROUND);

                    wrapperPanelB.add(textComponent);
                    textComponentsB.add(textComponent);

                    wrapperPanelA.add(makeSeparator(INSERTED_LINES_BACKGROUND));

                    break;

                default:
                    throw new RuntimeException("unexpected diff item type: " + item.getType());
            }
        }

        wrapperPanelA.add(Box.createVerticalGlue());
        wrapperPanelB.add(Box.createVerticalGlue());

        fileAScrollPane.getViewport().setView(wrapperPanelA);
        fileBScrollPane.getViewport().setView(wrapperPanelB);
    }

    private static String decodeStrings(TextsLinesEncoding textsLinesEncoding, DiffItem item) {
        return Arrays.stream(DiffGeneratorUtils.decodeText(item.getChars(), textsLinesEncoding))
                .collect(Collectors.joining("\n"));
    }

    private void calculateSideBySideScrolling() {
        List<BoundScrollRange> scrollRangesA = new ArrayList<>();
        List<BoundScrollRange> scrollRangesB = new ArrayList<>();

        int editorPanesAIndex = 0;
        int editorPanesBIndex = 0;

        for (DiffItem item : diffItems) {
            JComponent textComponent;
            switch (item.getType()) {
                case EQUAL:
                    JComponent textComponentA = textComponentsA.get(editorPanesAIndex++);
                    JComponent textComponentB = textComponentsB.get(editorPanesBIndex++);

                    scrollRangesA.add(new BoundScrollRange(
                            textComponentA.getY(),
                            textComponentA.getY() + textComponentA.getHeight(),
                            textComponentB.getY(),
                            true
                    ));

                    scrollRangesB.add(new BoundScrollRange(
                            textComponentB.getY(),
                            textComponentB.getY() + textComponentB.getHeight(),
                            textComponentA.getY(),
                            true
                    ));

                    break;

                case DELETE:
                    textComponent = textComponentsA.get(editorPanesAIndex++);

                    scrollRangesA.add(new BoundScrollRange(
                            textComponent.getY(),
                            textComponent.getY() + textComponent.getHeight(),
                            !scrollRangesB.isEmpty() ? scrollRangesB.get(scrollRangesB.size() - 1).endThis : 0,
                            false
                    ));

                    break;

                case INSERT:
                    textComponent = textComponentsB.get(editorPanesBIndex++);

                    scrollRangesB.add(new BoundScrollRange(
                            textComponent.getY(),
                            textComponent.getY() + textComponent.getHeight(),
                            !scrollRangesB.isEmpty() ? scrollRangesA.get(scrollRangesA.size() - 1).endThis : 0,
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

        fileAScrollPane.getViewport().addChangeListener(scrollListenerA);
        fileBScrollPane.getViewport().addChangeListener(scrollListenerB);
    }

    private JTextArea makeTextComponent(String text, Color backgroundColor) {
        JTextArea textArea = new JTextArea(text);
        textArea.setEditable(false); // TODO
        textArea.setMargin(new Insets(0, 0, 0, 0));
        textArea.setBackground(backgroundColor);
        textArea.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        textArea.setMinimumSize(new Dimension(0, textArea.getPreferredSize().height));
        textArea.setLineWrap(false);
        return textArea;
    }

    private JSeparator makeSeparator(Color color) {
        // TODO separator does not resize down; fix this
        JSeparator separator = new JSeparator(JSeparator.HORIZONTAL);
        separator.setBackground(color);
        separator.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        separator.setMinimumSize(new Dimension(1, 5));
        return separator;
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

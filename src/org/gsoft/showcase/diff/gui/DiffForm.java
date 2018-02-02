package org.gsoft.showcase.diff.gui;

import org.gsoft.showcase.diff.logic.*;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.List;

public class DiffForm extends JFrame {
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

    private final List<DiffListItem> diffItems;

    private JPanel rootPanel;
    private JLabel fileAPathLabel;
    private JLabel fileBPathLabel;
    private JScrollPane fileAScrollPane;
    private JScrollPane fileBScrollPane;

    private List<JEditorPane> editorPanesA;
    private List<JEditorPane> editorPanesB;

    private ScrollListener scrollListenerA;
    private ScrollListener scrollListenerB;

    public DiffForm(String fileAPath, String fileBPath, List<DiffListItem> diffItems) {
        this.diffItems = diffItems;

        setTitle("Diff");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(800, 600));

        fileAPathLabel.setText(fileAPath);
        fileBPathLabel.setText(fileBPath);

        populateDiffPanes();

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                calculateSideBySideScrolling();
            }
        });

        setContentPane(rootPanel);
        pack();
    }

    private void populateDiffPanes() {
        JPanel wrapperPanelA = new JPanel();
        JPanel wrapperPanelB = new JPanel();

        wrapperPanelA.setLayout(new BoxLayout(wrapperPanelA, BoxLayout.PAGE_AXIS));
        wrapperPanelB.setLayout(new BoxLayout(wrapperPanelB, BoxLayout.PAGE_AXIS));

        editorPanesA = new ArrayList<>();
        editorPanesB = new ArrayList<>();

        for (DiffListItem item : diffItems) {
            item.accept(new DiffListItemVisitor() {
                @Override
                public void visit(EqualStringsItem item) {
                    JEditorPane editorPaneA = makeEditorPane(item.getStrings(), Color.WHITE);
                    JEditorPane editorPaneB = makeEditorPane(item.getStrings(), Color.WHITE);

                    wrapperPanelA.add(editorPaneA);
                    editorPanesA.add(editorPaneA);

                    wrapperPanelB.add(editorPaneB);
                    editorPanesB.add(editorPaneB);
                }

                @Override
                public void visit(DeletedStringsItem item) {
                    JEditorPane editorPane = makeEditorPane(item.getStrings(), Color.RED);

                    wrapperPanelA.add(editorPane);
                    editorPanesA.add(editorPane);

                    wrapperPanelB.add(makeSeparator(Color.RED));
                }

                @Override
                public void visit(InsertedStringsItem item) {
                    JEditorPane editorPane = makeEditorPane(item.getStrings(), Color.GREEN);

                    wrapperPanelB.add(editorPane);
                    editorPanesB.add(editorPane);

                    wrapperPanelA.add(makeSeparator(Color.GREEN));
                }
            });
        }

        wrapperPanelA.add(Box.createVerticalGlue());
        wrapperPanelB.add(Box.createVerticalGlue());

        fileAScrollPane.getViewport().setView(wrapperPanelA);
        fileBScrollPane.getViewport().setView(wrapperPanelB);
    }

    private void calculateSideBySideScrolling() {
        List<BoundScrollRange> scrollRangesA = new ArrayList<>();
        List<BoundScrollRange> scrollRangesB = new ArrayList<>();

        DiffListItemVisitor diffItemVisitor = new DiffListItemVisitor() {
            private int editorPanesAIndex = 0;
            private int editorPanesBIndex = 0;

            @Override
            public void visit(EqualStringsItem item) {
                JEditorPane editorPaneA = editorPanesA.get(editorPanesAIndex++);
                JEditorPane editorPaneB = editorPanesB.get(editorPanesBIndex++);

                scrollRangesA.add(new BoundScrollRange(
                        editorPaneA.getY(),
                        editorPaneA.getY() + editorPaneA.getHeight(),
                        editorPaneB.getY(),
                        true
                ));

                scrollRangesB.add(new BoundScrollRange(
                        editorPaneB.getY(),
                        editorPaneB.getY() + editorPaneB.getHeight(),
                        editorPaneA.getY(),
                        true
                ));
            }

            @Override
            public void visit(DeletedStringsItem item) {
                JEditorPane editorPane = editorPanesA.get(editorPanesAIndex++);

                scrollRangesA.add(new BoundScrollRange(
                        editorPane.getY(),
                        editorPane.getY() + editorPane.getHeight(),
                        scrollRangesB.get(scrollRangesB.size() - 1).endThis,
                        false
                ));
            }

            @Override
            public void visit(InsertedStringsItem item) {
                JEditorPane editorPane = editorPanesB.get(editorPanesBIndex++);

                scrollRangesB.add(new BoundScrollRange(
                        editorPane.getY(),
                        editorPane.getY() + editorPane.getHeight(),
                        scrollRangesA.get(scrollRangesA.size() - 1).endThis,
                        false
                ));
            }
        };

        for (DiffListItem item : diffItems) {
            item.accept(diffItemVisitor);
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

    private JEditorPane makeEditorPane(String text, Color backgroundColor) {
        JEditorPane editorPane = new JEditorPane("text/plain",
                text.endsWith("\n") ? text.substring(0, text.length() - 1) : text);
        editorPane.setEditable(false); // TODO
        editorPane.setMargin(new Insets(0, 0, 0, 0));
        editorPane.setBackground(backgroundColor);
        editorPane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        return editorPane;
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

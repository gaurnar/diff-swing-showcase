package org.gsoft.showcase.diff.gui;

import org.gsoft.showcase.diff.logic.*;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
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

    private JPanel rootPanel;
    private JLabel fileAPathLabel;
    private JLabel fileBPathLabel;
    private JScrollPane fileAScrollPane;
    private JScrollPane fileBScrollPane;

    public DiffForm(String fileAPath, String fileBPath, List<DiffListItem> diffItems) {
        setTitle("Diff");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(400, 200));

        fileAPathLabel.setText(fileAPath);
        fileBPathLabel.setText(fileBPath);

        populateDiffPanes(diffItems);

        setContentPane(rootPanel);
        pack(); // TODO why do we need this one?
    }

    private void populateDiffPanes(List<DiffListItem> diffItems) {
        JPanel wrapperPanelA = new JPanel();
        JPanel wrapperPanelB = new JPanel();

        wrapperPanelA.setLayout(new BoxLayout(wrapperPanelA, BoxLayout.PAGE_AXIS));
        wrapperPanelB.setLayout(new BoxLayout(wrapperPanelB, BoxLayout.PAGE_AXIS));

        List<JEditorPane> editorPanesA = new ArrayList<>();
        List<JEditorPane> editorPanesB = new ArrayList<>();

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

                    // TODO breaks synchronized scrolling
//                    wrapperPanelB.add(makeSeparator(Color.RED));
                }

                @Override
                public void visit(InsertedStringsItem item) {
                    JEditorPane editorPane = makeEditorPane(item.getStrings(), Color.GREEN);

                    wrapperPanelB.add(editorPane);
                    editorPanesB.add(editorPane);

                    // TODO breaks synchronized scrolling
//                    wrapperPanelA.add(makeSeparator(Color.GREEN));
                }
            });
        }

        fileAScrollPane.getViewport().setView(wrapperPanelA);
        fileBScrollPane.getViewport().setView(wrapperPanelB);

        SwingUtilities.invokeLater(() -> { // invoking later (when frame is packed and coordinates are available)
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

            fileAScrollPane.getViewport().addChangeListener(
                    new ScrollListener(fileAScrollPane, fileBScrollPane, scrollRangesA));
            fileBScrollPane.getViewport().addChangeListener(
                    new ScrollListener(fileBScrollPane, fileAScrollPane, scrollRangesB));
        });
    }

    private JEditorPane makeEditorPane(String text, Color backgroundColor) {
        JEditorPane editorPane = new JEditorPane("text/plain", text);

        editorPane.setEditable(false); // TODO

        editorPane.setMargin(new Insets(0, 0, 0, 0));

        editorPane.setBackground(backgroundColor);

        Dimension preferredSize = editorPane.getPreferredSize();
        editorPane.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                // TODO remove unnecessary bottom margin properly
                preferredSize.height - editorPane.getFontMetrics(editorPane.getFont()).getHeight()));

        return editorPane;
    }

    private JSeparator makeSeparator(Color color) {
        JSeparator separator = new JSeparator(JSeparator.HORIZONTAL);
        separator.setBackground(color);
        separator.setMinimumSize(new Dimension(1, 3));
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

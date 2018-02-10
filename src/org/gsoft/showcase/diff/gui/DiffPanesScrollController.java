package org.gsoft.showcase.diff.gui;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DiffPanesScrollController {
    public static final class DiffItemPosition {
        private final int startA, startB;
        private final int endA, endB;
        private final DiffForm.ExtendedDiffItemType type;

        public DiffItemPosition(int startA, int startB,
                                int endA, int endB,
                                DiffForm.ExtendedDiffItemType type) {
            this.startA = startA;
            this.startB = startB;
            this.endA = endA;
            this.endB = endB;
            this.type = type;
        }

        public int getStartA() {
            return startA;
        }

        public int getStartB() {
            return startB;
        }

        public int getEndA() {
            return endA;
        }

        public int getEndB() {
            return endB;
        }

        public DiffForm.ExtendedDiffItemType getType() {
            return type;
        }
    }

    private static final class BoundScrollRange {
        final int startThis;
        final int endThis;
        final int startOther;
        final boolean scrollOther;
        final int diffItemIndex;

        private BoundScrollRange(int startThis, int endThis,
                                 int startOther, boolean scrollOther,
                                 int diffItemIndex) {
            this.startThis = startThis;
            this.endThis = endThis;
            this.startOther = startOther;
            this.scrollOther = scrollOther;
            this.diffItemIndex = diffItemIndex;
        }
    }

    private final JScrollPane scrollPaneA;
    private final JScrollPane scrollPaneB;

    private final DiffMatchingImagePanel diffMatchingImagePanel;

    private final List<DiffItemPosition> diffItemPositions;

    private List<BoundScrollRange> scrollRangesA;
    private List<BoundScrollRange> scrollRangesB;

    private BoundScrollRange currentScrollRangeA;
    private BoundScrollRange currentScrollRangeB;

    private int currentDiffItemIndex;

    private boolean scrollPending = false;
    private boolean changesScrolling = false;

    public DiffPanesScrollController(JScrollPane scrollPaneA,
                                     JScrollPane scrollPaneB,
                                     JFrame diffFrame,
                                     DiffMatchingImagePanel diffMatchingImagePanel,
                                     List<DiffItemPosition> diffItemPositions) {
        this.scrollPaneA = scrollPaneA;
        this.scrollPaneB = scrollPaneB;
        this.diffMatchingImagePanel = diffMatchingImagePanel;
        this.diffItemPositions = new ArrayList<>(diffItemPositions);

        scrollPaneA.getViewport().addChangeListener(this::onScrollStateChanged);
        scrollPaneB.getViewport().addChangeListener(this::onScrollStateChanged);

        diffFrame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                try {
                    updateScrollRanges();
                } catch (BadLocationException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
    }

    public void scrollToPreviousChange() {
        if (currentDiffItemIndex == 0) {
            return; // no previous item
        }

        DiffItemPosition previousItem = diffItemPositions.get(currentDiffItemIndex - 1);

        if (previousItem.type == DiffForm.ExtendedDiffItemType.EQUAL) {
            if (currentDiffItemIndex == 1) {
                return; // no previous item
            }
            currentDiffItemIndex -= 2; // skipping EQUAL item
        } else {
            currentDiffItemIndex--;
        }

        scrollLeftPaneToCurrentDiffItemPosition();
    }

    public void scrollToNextChange() {
        if (currentDiffItemIndex == diffItemPositions.size() - 1) {
            return; // no next item
        }

        DiffItemPosition nextItem = diffItemPositions.get(currentDiffItemIndex + 1);

        if (nextItem.type == DiffForm.ExtendedDiffItemType.EQUAL) {
            if (currentDiffItemIndex == diffItemPositions.size() - 2) {
                return; // no next item
            }
            currentDiffItemIndex += 2; // skipping EQUAL item
        } else {
            currentDiffItemIndex++;
        }

        scrollLeftPaneToCurrentDiffItemPosition();
    }

    private void onScrollStateChanged(ChangeEvent e) {
        if (scrollPending) {
            return;
        }

        JViewport sourceViewport = (JViewport) e.getSource();

        JScrollPane thisScrollPane;
        BoundScrollRange currentScrollRange;
        List<BoundScrollRange> scrollRanges;
        JScrollPane otherScrollPane;

        if (sourceViewport == scrollPaneA.getViewport()) {
            thisScrollPane = scrollPaneA;
            currentScrollRange = currentScrollRangeA;
            scrollRanges = scrollRangesA;
            otherScrollPane = scrollPaneB;
        } else if (sourceViewport == scrollPaneB.getViewport()) {
            thisScrollPane = scrollPaneB;
            currentScrollRange = currentScrollRangeB;
            scrollRanges = scrollRangesB;
            otherScrollPane = scrollPaneA;
        } else {
            throw new RuntimeException("unknown scroll pane!");
        }

        if (scrollRanges == null) {
            // not initialized
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

        if (thisScrollPane == scrollPaneA) {
            currentScrollRangeA = currentScrollRange;
        } else { // scrollPaneB
            currentScrollRangeB = currentScrollRange;
        }

        if (!changesScrolling) {
            currentDiffItemIndex = currentScrollRange.diffItemIndex;
        }

        Point otherPosition = otherScrollPane.getViewport().getViewPosition();

        scrollPending = true; // to avoid cycles

        setViewportCenterPosition(otherScrollPane, new Point(otherPosition.x,
                currentScrollRange.scrollOther ?
                        currentScrollRange.startOther + thisCenterPosition.y - currentScrollRange.startThis
                        : currentScrollRange.startOther
        ));

        try {
            diffMatchingImagePanel.setItemPositions(getDiffItemPositionsInViewport());
        } catch (BadLocationException ex) {
            throw new RuntimeException(ex);
        }
        diffMatchingImagePanel.repaint();

        SwingUtilities.invokeLater(() -> scrollPending = false);
    }

    private void updateScrollRanges() throws BadLocationException {
        JTextArea textAreaA = (JTextArea) scrollPaneA.getViewport().getView();
        JTextArea textAreaB = (JTextArea) scrollPaneB.getViewport().getView();

        scrollRangesA = new ArrayList<>();
        scrollRangesB = new ArrayList<>();

        for (int i = 0; i < diffItemPositions.size(); i++) {
            DiffItemPosition item = diffItemPositions.get(i);
            switch (item.type) {
                case EQUAL:
                case MODIFIED:
                    Rectangle firstCharRectA = textAreaA.modelToView(item.startA);
                    Rectangle lastCharRectA = textAreaA.modelToView(item.endA);

                    Rectangle firstCharRectB = textAreaB.modelToView(item.startB);
                    Rectangle lastCharRectB = textAreaB.modelToView(item.endB);

                    scrollRangesA.add(new BoundScrollRange(
                            firstCharRectA.getLocation().y,
                            lastCharRectA.getLocation().y + lastCharRectA.height,
                            firstCharRectB.getLocation().y,
                            true,
                            i));

                    scrollRangesB.add(new BoundScrollRange(
                            firstCharRectB.getLocation().y,
                            lastCharRectB.getLocation().y + lastCharRectB.height,
                            firstCharRectA.getLocation().y,
                            true,
                            i));

                    break;

                case DELETE:
                    firstCharRectA = textAreaA.modelToView(item.startA);
                    lastCharRectA = textAreaA.modelToView(item.endA);

                    firstCharRectB = textAreaB.modelToView(item.startB);

                    scrollRangesA.add(new BoundScrollRange(
                            firstCharRectA.getLocation().y,
                            lastCharRectA.getLocation().y + lastCharRectA.height,
                            firstCharRectB.getLocation().y,
                            false,
                            i));

                    break;

                case INSERT:
                    firstCharRectB = textAreaB.modelToView(item.startB);
                    lastCharRectB = textAreaB.modelToView(item.endB);

                    firstCharRectA = textAreaA.modelToView(item.startA);

                    scrollRangesB.add(new BoundScrollRange(
                            firstCharRectB.getLocation().y,
                            lastCharRectB.getLocation().y + lastCharRectB.height,
                            firstCharRectA.getLocation().y,
                            false,
                            i));

                    break;

                default:
                    throw new RuntimeException("unexpected diff item type: " + item.type);
            }
        }

        diffMatchingImagePanel.setItemPositions(getDiffItemPositionsInViewport());
        diffMatchingImagePanel.repaint();
    }

    private void scrollLeftPaneToCurrentDiffItemPosition() {
        JTextArea textAreaA = (JTextArea) scrollPaneA.getViewport().getView();

        Rectangle rect;

        try {
            rect = textAreaA.modelToView(diffItemPositions.get(currentDiffItemIndex).startA);
        } catch (BadLocationException e) {
            throw new RuntimeException(e);
        }

        changesScrolling = true;

        setViewportCenterPosition(scrollPaneA, new Point(rect.x, rect.y));

        SwingUtilities.invokeLater(() -> changesScrolling = false);
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

    private List<DiffItemPosition> getDiffItemPositionsInViewport() throws BadLocationException {
        // TODO fix issue with positioning near end of files

        if (scrollRangesA == null || scrollRangesB == null) {
            return Collections.emptyList(); // not yet initialized
        }

        int[] boundsA = getDiffItemBoundsInViewport(scrollPaneA, scrollRangesA);
        int[] boundsB = getDiffItemBoundsInViewport(scrollPaneB, scrollRangesB);

        if (boundsA == null || boundsB == null) {
            return Collections.emptyList(); // not yet initialized?
        }

        int minItemIndex = Math.min(boundsA[0], boundsB[0]);
        int maxItemIndex = Math.max(boundsA[1], boundsB[1]);

        List<DiffItemPosition> result = new ArrayList<>(maxItemIndex - minItemIndex);

        JTextArea textAreaA = (JTextArea) scrollPaneA.getViewport().getView();
        JTextArea textAreaB = (JTextArea) scrollPaneB.getViewport().getView();

        int viewportAPosition = scrollPaneA.getViewport().getViewPosition().y;
        int viewportBPosition = scrollPaneB.getViewport().getViewPosition().y;

        for (int i = minItemIndex; i <= maxItemIndex; i++) {
            DiffItemPosition position = diffItemPositions.get(i);
            Rectangle endARect = textAreaA.modelToView(position.endA);
            Rectangle endBRect = textAreaB.modelToView(position.endB);
            result.add(new DiffItemPosition(
                    textAreaA.modelToView(position.startA).y - viewportAPosition,
                    textAreaB.modelToView(position.startB).y - viewportBPosition,
                    endARect.y + endARect.height - viewportAPosition,
                    endBRect.y + endBRect.height - viewportBPosition,
                    position.type
            ));
        }

        return result;
    }

    private static int[] getDiffItemBoundsInViewport(JScrollPane scrollPane,
                                                     List<BoundScrollRange> scrollRanges) {
        Rectangle paneViewRect = scrollPane.getViewport().getViewRect();

        //
        // TODO use binary search
        //

        int minItemIndex = -1;
        for (int i = 0; i < scrollRanges.size(); i++) {
            if (scrollRanges.get(i).endThis > paneViewRect.y) {
                minItemIndex = scrollRanges.get(i).diffItemIndex;
                break;
            }
        }

        int maxItemIndex = -1;
        for (int i = scrollRanges.size() - 1; i > 0; i--) {
            if (scrollRanges.get(i).startThis < paneViewRect.y + paneViewRect.height) {
                maxItemIndex = scrollRanges.get(i).diffItemIndex;
                break;
            }
        }

        if (minItemIndex == -1 || maxItemIndex == -1) {
            return null; // TODO is it possible?
        }

        return new int[] { minItemIndex, maxItemIndex };
    }

    private static Point getViewportCenterPosition(JScrollPane scrollPane) {
        JViewport viewport = scrollPane.getViewport();
        Point topViewPosition = viewport.getViewPosition();
        return new Point(topViewPosition.x, topViewPosition.y + viewport.getHeight() / 2);
    }

    private static void setViewportCenterPosition(JScrollPane scrollPane, Point centerPoint) {
        JViewport viewport = scrollPane.getViewport();
        viewport.setViewPosition(new Point(centerPoint.x, Math.max(0, centerPoint.y - viewport.getHeight() / 2)));
        scrollPane.repaint();
    }
}

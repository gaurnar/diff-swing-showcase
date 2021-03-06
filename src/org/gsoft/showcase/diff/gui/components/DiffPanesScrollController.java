package org.gsoft.showcase.diff.gui.components;

import org.gsoft.showcase.diff.gui.logic.DiffItemPosition;
import org.gsoft.showcase.diff.gui.logic.ExtendedDiffItemType;

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
    private static final class LinkedScrollRange {
        final int startThis;
        final int endThis;
        final int startOther;
        final boolean scrollOther;
        final int diffItemIndex;

        private LinkedScrollRange(int startThis, int endThis,
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

    private List<LinkedScrollRange> scrollRangesA;
    private List<LinkedScrollRange> scrollRangesB;

    private LinkedScrollRange currentScrollRangeA;
    private LinkedScrollRange currentScrollRangeB;

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

        if (previousItem.getType() == ExtendedDiffItemType.EQUAL) {
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

        if (nextItem.getType() == ExtendedDiffItemType.EQUAL) {
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
        LinkedScrollRange currentScrollRange;
        List<LinkedScrollRange> scrollRanges;
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
            int scrollRangeIndex = rangesBinarySearchByPosition(thisCenterPosition.y, scrollRanges);
            if (scrollRangeIndex == -1) {
                // not found
                return;
            }
            currentScrollRange = scrollRanges.get(scrollRangeIndex);
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
            switch (item.getType()) {
                case EQUAL:
                case MODIFIED:
                    Rectangle firstCharRectA = textAreaA.modelToView(item.getStartA());
                    Rectangle lastCharRectA = textAreaA.modelToView(item.getEndA());

                    Rectangle firstCharRectB = textAreaB.modelToView(item.getStartB());
                    Rectangle lastCharRectB = textAreaB.modelToView(item.getEndB());

                    scrollRangesA.add(new LinkedScrollRange(
                            firstCharRectA.getLocation().y,
                            lastCharRectA.getLocation().y + lastCharRectA.height,
                            firstCharRectB.getLocation().y,
                            true,
                            i));

                    scrollRangesB.add(new LinkedScrollRange(
                            firstCharRectB.getLocation().y,
                            lastCharRectB.getLocation().y + lastCharRectB.height,
                            firstCharRectA.getLocation().y,
                            true,
                            i));

                    break;

                case DELETE:
                    firstCharRectA = textAreaA.modelToView(item.getStartA());
                    lastCharRectA = textAreaA.modelToView(item.getEndA());

                    firstCharRectB = textAreaB.modelToView(item.getStartB());

                    scrollRangesA.add(new LinkedScrollRange(
                            firstCharRectA.getLocation().y,
                            lastCharRectA.getLocation().y + lastCharRectA.height,
                            firstCharRectB.getLocation().y,
                            false,
                            i));

                    break;

                case INSERT:
                    firstCharRectB = textAreaB.modelToView(item.getStartB());
                    lastCharRectB = textAreaB.modelToView(item.getEndB());

                    firstCharRectA = textAreaA.modelToView(item.getStartA());

                    scrollRangesB.add(new LinkedScrollRange(
                            firstCharRectB.getLocation().y,
                            lastCharRectB.getLocation().y + lastCharRectB.height,
                            firstCharRectA.getLocation().y,
                            false,
                            i));

                    break;

                default:
                    throw new RuntimeException("unexpected diff item type: " + item.getType());
            }
        }

        diffMatchingImagePanel.setItemPositions(getDiffItemPositionsInViewport());
        diffMatchingImagePanel.repaint();
    }

    private void scrollLeftPaneToCurrentDiffItemPosition() {
        TextAreaWithOutlineRect textAreaA = (TextAreaWithOutlineRect) scrollPaneA.getViewport().getView();
        TextAreaWithOutlineRect textAreaB = (TextAreaWithOutlineRect) scrollPaneB.getViewport().getView();

        DiffItemPosition currentItemPosition = diffItemPositions.get(currentDiffItemIndex);

        Rectangle startRect;

        try {
            startRect = textAreaA.modelToView(currentItemPosition.getStartA());

            textAreaA.setHighlightRect(null);
            textAreaB.setHighlightRect(null);

            if (currentItemPosition.getType() != ExtendedDiffItemType.INSERT) {
                // adding outline to textAreaA
                Rectangle outlineEndRect = textAreaA.modelToView(currentItemPosition.getEndA());

                textAreaA.setHighlightRect(new Rectangle(0, startRect.y,
                        textAreaA.getWidth(),outlineEndRect.y + outlineEndRect.height - startRect.y));
            }

            if (currentItemPosition.getType() != ExtendedDiffItemType.DELETE) {
                // adding outline to textAreaB
                Rectangle outlineStartRect = textAreaB.modelToView(currentItemPosition.getStartB());
                Rectangle outlineEndRect = textAreaB.modelToView(currentItemPosition.getEndB());

                textAreaB.setHighlightRect(new Rectangle(0, outlineStartRect.y,
                        textAreaB.getWidth(),outlineEndRect.y + outlineEndRect.height - outlineStartRect.y));
            }
        } catch (BadLocationException e) {
            throw new RuntimeException(e);
        }

        // for outline drawing
        textAreaA.repaint(scrollPaneA.getViewport().getViewRect());
        textAreaB.repaint(scrollPaneB.getViewport().getViewRect());

        changesScrolling = true;

        setViewportCenterPosition(scrollPaneA, new Point(startRect.x, startRect.y));

        SwingUtilities.invokeLater(() -> changesScrolling = false);
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
            Rectangle endARect = textAreaA.modelToView(position.getEndA());
            Rectangle endBRect = textAreaB.modelToView(position.getEndB());
            result.add(new DiffItemPosition(
                    textAreaA.modelToView(position.getStartA()).y - viewportAPosition,
                    textAreaB.modelToView(position.getStartB()).y - viewportBPosition,
                    endARect.y + endARect.height - viewportAPosition,
                    endBRect.y + endBRect.height - viewportBPosition,
                    position.getType()
            ));
        }

        return result;
    }

    private static int[] getDiffItemBoundsInViewport(JScrollPane scrollPane,
                                                     List<LinkedScrollRange> scrollRanges) {
        Rectangle paneViewRect = scrollPane.getViewport().getViewRect();

        int minRangeIndex = rangesBinarySearchByPosition(paneViewRect.y, scrollRanges);
        if (minRangeIndex == -1) {
            return null;
        }

        int maxRangeIndex = minRangeIndex;
        while ((maxRangeIndex < scrollRanges.size() - 1) &&
                (scrollRanges.get(maxRangeIndex).startThis < paneViewRect.y + paneViewRect.height)) {
            maxRangeIndex++;
        }

        return new int[] {
                scrollRanges.get(minRangeIndex).diffItemIndex,
                scrollRanges.get(maxRangeIndex).diffItemIndex
        };
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

    private static int rangesBinarySearchByPosition(int pos, List<LinkedScrollRange> rangesSorted) {
        if (rangesSorted.isEmpty()) {
            return -1;
        }

        if (rangesSorted.get(0).startThis > pos) {
            // special case due to text area padding (first range starts not at zero)
            return 0;
        }

        int lo = 0;
        int hi = rangesSorted.size() - 1;
        while (lo <= hi) {
            int mid = lo + (hi - lo) / 2;
            LinkedScrollRange midRange = rangesSorted.get(mid);
            if (midRange.startThis > pos) hi = mid - 1;
            else if (midRange.endThis < pos) lo = mid + 1;
            else return mid;
        }
        return -1;
    }
}

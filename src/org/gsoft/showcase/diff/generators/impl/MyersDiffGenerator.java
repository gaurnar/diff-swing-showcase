package org.gsoft.showcase.diff.generators.impl;

import org.gsoft.showcase.diff.generators.DiffGenerator;
import org.gsoft.showcase.diff.generators.DiffItem;
import org.gsoft.showcase.diff.generators.DiffItemType;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Diff generator implemented as described in:
 * Myers E. An O(ND) Difference Algorithm and Its Variations.
 *
 * Available at:
 * https://neil.fraser.name/software/diff_match_patch/myers.pdf
 */
public final class MyersDiffGenerator implements DiffGenerator {
    private static final class EditPathVertex {
        final int x, y;

        EditPathVertex(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    @Override
    public List<DiffItem> generate(int[] a, int[] b) {
        List<DiffItem> diffItems = new ArrayList<>();

        List<Integer> equalCharsPending = new ArrayList<>();
        List<Integer> insertedCharsPending = new ArrayList<>();
        List<Integer> deletedCharsPending = new ArrayList<>();

        int prevX = 0, prevY = 0;

        List<EditPathVertex> editPathVertices = doMyers(a, b);

        for (EditPathVertex e : editPathVertices) {
            if (e.x > prevX && e.y > prevY) {
                // diagonal edge = equal char
                flushPendingToDiffItems(null, insertedCharsPending, deletedCharsPending, diffItems);
                equalCharsPending.add(charAt(a, e.x));
            } else if (e.x > prevX) {
                // horizontal edge = deleted char
                flushPendingToDiffItems(equalCharsPending, insertedCharsPending, null, diffItems);
                deletedCharsPending.add(charAt(a, e.x));
            } else {
                // vertical edge = inserted char
                flushPendingToDiffItems(equalCharsPending, null, deletedCharsPending, diffItems);
                insertedCharsPending.add(charAt(b, e.y));
            }

            prevX = e.x;
            prevY = e.y;
        }

        flushPendingToDiffItems(equalCharsPending, insertedCharsPending, deletedCharsPending, diffItems);

        return diffItems;
    }

    private static void flushPendingToDiffItems(List<Integer> equalCharsPending,
                                         List<Integer> insertedCharsPending,
                                         List<Integer> deletedCharsPending,
                                         List<DiffItem> diffItems) {
        if ((equalCharsPending != null) && !equalCharsPending.isEmpty()) {
            diffItems.add(new DiffItem(DiffItemType.EQUAL, charsListToArray(equalCharsPending)));
            equalCharsPending.clear();
        }
        if ((insertedCharsPending != null) && !insertedCharsPending.isEmpty()) {
            diffItems.add(new DiffItem(DiffItemType.INSERT, charsListToArray(insertedCharsPending)));
            insertedCharsPending.clear();
        }
        if ((deletedCharsPending != null) && !deletedCharsPending.isEmpty()) {
            diffItems.add(new DiffItem(DiffItemType.DELETE, charsListToArray(deletedCharsPending)));
            deletedCharsPending.clear();
        }
    }

    /**
     * emulating V being [-MAX .. MAX] array
     */
    private static final class V {
        final int[] nonNegativeV;
        final int[] negativeV;

        V(int D, V previous) {
            nonNegativeV = new int[D + 1];
            negativeV = new int[D + 1];

            if (previous != null) {
                // copying from previous iteration
                System.arraycopy(previous.nonNegativeV, 0,
                        nonNegativeV, 0, previous.nonNegativeV.length);
                System.arraycopy(previous.negativeV, 0,
                        negativeV, 0, previous.negativeV.length);
            }
        }

        int get(int i) {
            if (i >= 0) {
                if (i < nonNegativeV.length) {
                    return nonNegativeV[i];
                } else {
                    return 0;
                }
            } else {
                if (Math.abs(i) < negativeV.length) {
                    return negativeV[Math.abs(i)];
                } else {
                    return 0;
                }
            }
        }

        void set(int i, int value) {
            if (i >= 0) {
                nonNegativeV[i] = value;
            } else {
                negativeV[Math.abs(i)] = value;
            }
        }
    }

    private static final class Vd {
        /**
         * need to store V for every D iteration (needed for reconstructEditPath)
         */
        final ArrayList<V> vs;

        Vd() {
            vs = new ArrayList<>();
        }

        V get(int D) {
            if (D < vs.size()) {
                return vs.get(D);
            } else {
                V newV = new V(D, D > 0 ? vs.get(D - 1) : null);
                vs.add(newV);
                return newV;
            }
        }
    }

    private static List<EditPathVertex> doMyers(int[] a, int[] b) {
        // Simple, unoptimized version as described on p. 6.
        // TODO implement optimized version

        final int N = a.length;
        final int M = b.length;

        Vd Vd = new Vd();

        for (int D = 0; D <= N + M; D++) {
            for (int k = -D; k <= D; k += 2) {
                int x, y;
                if ((k == -D) || (k != D) && Vd.get(D).get(k - 1) < Vd.get(D).get(k + 1)) {
                    x = Vd.get(D).get(k + 1);
                } else {
                    x = Vd.get(D).get(k - 1) + 1;
                }
                y = x - k;
                while (x < N && y < M && charAt(a, x + 1) == charAt(b, y + 1)) {
                    x++; y++;
                }
                Vd.get(D).set(k, x);
                if (x >= N && y >= M) {
                    return reconstructEditPath(N, M, D, Vd, a, b);
                }
            }
        }
        throw new RuntimeException("bogus edit script length"); // should not happen
    }

    /**
     * Based on description on p. 8 of referenced paper (unoptimized version).
     * Switched from recursion to loop in order to avoid StackOverflowError.
     *
     * TODO implement optimized version
     */
    private static LinkedList<EditPathVertex> reconstructEditPath(int N, int M, int D, Vd Vd, int[] a, int[] b) {
        int k = N - M;

        LinkedList<EditPathVertex> result = new LinkedList<>();

        while (true) {
            int kx = Vd.get(D).get(k);
            int ky = kx - k;

            int x = kx;
            int y = ky;
            int currentSnakeLen = 0;
            int snakeLenWithNonDiagonalEdge = -1;

            while (x >= 1 && y >= 1 && charAt(a, x) == charAt(b, y)) {
                if (D != 0 &&
                        (Vd.get(D - 1).get(k + 1) == x || // vertical edge
                                Vd.get(D - 1).get(k - 1) == x - 1)) { // horizontal edge
                    snakeLenWithNonDiagonalEdge = currentSnakeLen;
                }

                currentSnakeLen++;
                x--;
                y--;
            }

            int snakeLen;

            if (snakeLenWithNonDiagonalEdge != -1) {
                snakeLen = snakeLenWithNonDiagonalEdge;
            } else {
                snakeLen = currentSnakeLen;
            }

            x = kx - snakeLen;
            y = ky - snakeLen;

            for (int i = snakeLen; i > 0; i--) { // adding "snake"
                // note inverted order because of addFirst
                result.addFirst(new EditPathVertex(x + i, y + i));
            }

            if (D == 0) {
                break;
            } else {
                if (Vd.get(D - 1).get(k + 1) == x) {
                    // vertical edge
                    k++; D--;
                } else if (Vd.get(D - 1).get(k - 1) == x - 1) {
                    // horizontal edge
                    k--; D--;
                } else {
                    throw new AssertionError("no non-diagonal edge before maximum snake");
                }
                result.addFirst(new EditPathVertex(x, y));
            }
        }

        return result;
    }

    /**
     * emulating string indexing starting with "1"
     */
    private static int charAt(int[] s, int i) {
        return s[i - 1];
    }

    private static int[] charsListToArray(List<Integer> list) {
        return list.stream().mapToInt(i -> i).toArray();
    }
}

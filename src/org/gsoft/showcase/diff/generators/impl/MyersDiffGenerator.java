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
    private static final int MAX_SCRIPT_SIZE = 10000;

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

    private static List<EditPathVertex> doMyers(int[] a, int[] b) {
        // Simple, unoptimized version as described on p. 6.
        // TODO implement optimized version

        final int N = a.length;
        final int M = b.length;

        // need to store V for every D iteration (needed for reconstructEditPath later)
        // TODO run algorithm twice (to allocate only for D iterations instead of MAX_SCRIPT_SIZE)
        final int[][] Vd = new int[MAX_SCRIPT_SIZE][];

        Vd[0] = new int[MAX_SCRIPT_SIZE * 2];

        for (int D = 0; D < MAX_SCRIPT_SIZE; D++) {
            if (D != 0) {
                Vd[D] = Vd[D - 1].clone();
            }
            for (int k = -D; k <= D; k += 2) {
                int x, y;
                if ((k == -D) || (k != D) && Vd[D][indexV(k - 1)] < Vd[D][indexV(k + 1)]) {
                    x = Vd[D][indexV(k + 1)];
                } else {
                    x = Vd[D][indexV(k - 1)] + 1;
                }
                y = x - k;
                while (x < N && y < M && charAt(a, x + 1) == charAt(b, y + 1)) {
                    x++; y++;
                }
                Vd[D][indexV(k)] = x;
                if (x >= N && y >= M) {
                    return reconstructEditPath(N, M, D, Vd, a, b);
                }
            }
        }
        throw new RuntimeException("edit script is too long!");
    }

    /**
     * Based on description on p. 8 of referenced paper (unoptimized version).
     * Switched from recursion to loop in order to avoid StackOverflowError.
     *
     * TODO implement optimized version
     */
    private static LinkedList<EditPathVertex> reconstructEditPath(int N, int M, int D, int[][] Vd, int[] a, int[] b) {
        int k = N - M;

        LinkedList<EditPathVertex> result = new LinkedList<>();

        while (true) {
            int kx = Vd[D][indexV(k)];
            int ky = kx - k;

            int x = kx;
            int y = ky;
            int currentSnakeLen = 0;
            int snakeLenWithNonDiagonalEdge = -1;

            while (x >= 1 && y >= 1 && charAt(a, x) == charAt(b, y)) {
                if (D != 0 &&
                        (Vd[D - 1][indexV(k + 1)] == x || // vertical edge
                                Vd[D - 1][indexV(k - 1)] == x - 1)) { // horizontal edge
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
                if (Vd[D - 1][indexV(k + 1)] == x) {
                    // vertical edge
                    k++; D--;
                } else if (Vd[D - 1][indexV(k - 1)] == x - 1) {
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
     * to emulate V being [-MAX .. MAX] array
     */
    private static int indexV(int i) {
        return i + MAX_SCRIPT_SIZE;
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

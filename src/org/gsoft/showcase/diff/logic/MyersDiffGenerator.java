package org.gsoft.showcase.diff.logic;

import java.util.ArrayList;
import java.util.List;

public final class MyersDiffGenerator implements DiffGenerator {
    private static final int MAX_SCRIPT_SIZE = 10;

    public static final class EditPathEdge {
        public final int x, y;

        private EditPathEdge(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    @Override
    public List<DiffListItem> generate(String contentA, String contentB) {
        // TODO implement
        return null;
    }

    public List<EditPathEdge> doMyers(String a, String b) {
        //
        // As described in:
        // Myers E. An O(ND) Difference Algorithm and Its Variations.
        //
        // Available at:
        // https://neil.fraser.name/software/diff_match_patch/myers.pdf
        //

        final int N = a.length();
        final int M = b.length();

        // need to store V for every D iteration (needed for reconstructEditPath later)
        // TODO run algorithm twice (to allocate only for D iterations instead of MAX_SCRIPT_SIZE)
        final int[][] Vd = new int[MAX_SCRIPT_SIZE][];

        Vd[0] = new int[MAX_SCRIPT_SIZE * 2];

//        Vd[0][indexV(1)] = 0; // redundant

        for (int D = 0; D <= MAX_SCRIPT_SIZE; D++) {
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
                    return reconstructEditPath(N - M, D, Vd, a, b);
                }
            }
        }
        throw new RuntimeException("edit script is too long!");
    }

    private List<EditPathEdge> reconstructEditPath(int k, int D, int[][] Vd, String a, String b) {
        // as described on p. 8 of referenced paper

        if (D == 0) {
            return new ArrayList<>();
        }

        int x, y;

        // first finding trailing "snake"
        int snakeLen = 0;
        x = Vd[D][indexV(k)];
        y = x - k;
        while (x >= 1 && y >= 1 && charAt(a, x) == charAt(b, y)) {
            x--; y--;
            snakeLen++;
        }

        List<EditPathEdge> recursedResult;

        if (Vd[D - 1][indexV(k + 1)] == x) {
            // vertical edge
            recursedResult = reconstructEditPath(k + 1, D - 1, Vd, a, b);
        } else {
            // horizontal edge
            recursedResult = reconstructEditPath(k - 1, D - 1, Vd, a, b);
        }

        recursedResult.add(new EditPathEdge(x, y));

        for (int i = 0; i < snakeLen; i++) {
            recursedResult.add(new EditPathEdge(++x, ++y));
        }

        return recursedResult;
    }

    /**
     * to emulate V being [-MAX .. MAX] array
     */
    private int indexV(int i) {
        return i + MAX_SCRIPT_SIZE;
    }

    /**
     * emulating string indexing starting with "1"
     */
    private char charAt(String s, int i) {
        return s.charAt(i - 1);
    }
}

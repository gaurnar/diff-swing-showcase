package org.gsoft.showcase.diff.generators;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class DiffGeneratorUtils {
    public static final class LinesEncoding {
        private final int[] linesA;
        private final int[] linesB;
        private final Map<Integer, String> linesDecodingMap;

        public LinesEncoding(int[] linesA, int[] linesB, Map<Integer, String> linesDecodingMap) {
            this.linesA = linesA;
            this.linesB = linesB;
            this.linesDecodingMap = linesDecodingMap;
        }

        public int[] getLinesA() {
            return linesA;
        }

        public int[] getLinesB() {
            return linesB;
        }

        public Map<Integer, String> getLinesDecodingMap() {
            return Collections.unmodifiableMap(linesDecodingMap);
        }
    }

    private DiffGeneratorUtils() {
        throw new UnsupportedOperationException();
    }

    /**
     * Encode string for passing to {@link DiffGenerator#generate(int[], int[])}.
     */
    public static int[] encodeString(String s) {
        int[] result = new int[s.length()];
        for (int i = 0; i < result.length; i++) {
            result[i] = s.charAt(i);
        }
        return result;
    }

    /**
     * Decode strings found in {@link DiffItem#chars}
     * (diff must be generated based on strings encoded with {@link DiffGeneratorUtils#encodeString(java.lang.String)})
     */
    public static String decodeString(int[] s) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int c : s) {
            stringBuilder.append((char) c);
        }
        return stringBuilder.toString();
    }

    /**
     * Encode text lines for passing to {@link DiffGenerator#generate(int[], int[])}.
     * Each int will represent single line.
     */
    public static LinesEncoding encodeLines(String[] linesA, String[] linesB) {
        Map<String, Integer> encodingMap = new HashMap<>(linesA.length);
        Map<Integer, String> decodingMap = new HashMap<>(linesA.length);

        int[] linesAEncoded = new int[linesA.length];
        int[] linesBEncoded = new int[linesB.length];

        int counter = encodeLines(Integer.MIN_VALUE, linesA, linesAEncoded, encodingMap, decodingMap);
        encodeLines(counter, linesB, linesBEncoded, encodingMap, decodingMap);

        return new LinesEncoding(linesAEncoded, linesBEncoded, decodingMap);
    }

    /**
     * Decode lines found in {@link DiffItem#chars}
     * (diff must be generated based on lines encoded with {@link DiffGeneratorUtils#encodeLines(java.lang.String[], java.lang.String[])})
     */
    public static String[] decodeLines(int[] lines, LinesEncoding encoding) {
        String[] result = new String[lines.length];
        for (int i = 0; i < lines.length; i++) {
            result[i] = encoding.getLinesDecodingMap().get(lines[i]);
        }
        return result;
    }

    private static int encodeLines(int counter, String[] lines, int[] encodedLines,
                                   Map<String, Integer> encodingMap,
                                   Map<Integer, String> decodingMap) {
        for (int i = 0; i < lines.length; i++) {
            String s = lines[i];
            int c;
            if (!encodingMap.containsKey(s)) {
                if (counter == Integer.MAX_VALUE) {
                    throw new RuntimeException("too many unique lines");
                }
                c = counter++;
                encodingMap.put(s, c);
                decodingMap.put(c, s);
            } else {
                c = encodingMap.get(s);
            }
            encodedLines[i] = c;
        }
        return counter;
    }
}

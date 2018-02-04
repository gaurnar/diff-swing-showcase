package org.gsoft.showcase.diff.logic;

import java.util.HashMap;
import java.util.Map;

public final class DiffGeneratorUtils {
    public static final class TextsLinesEncoding {
        private final int[] textA;
        private final int[] textB;
        private final Map<Integer, String> linesDecodingMap;

        public TextsLinesEncoding(int[] textA, int[] textB, Map<Integer, String> linesDecodingMap) {
            this.textA = textA;
            this.textB = textB;
            this.linesDecodingMap = linesDecodingMap;
        }

        public int[] getTextA() {
            return textA;
        }

        public int[] getTextB() {
            return textB;
        }

        public Map<Integer, String> getLinesDecodingMap() {
            return linesDecodingMap;
        }
    }

    private DiffGeneratorUtils() {
        throw new UnsupportedOperationException();
    }

    public static int[] encodeString(String s) {
        int[] result = new int[s.length()];
        for (int i = 0; i < result.length; i++) {
            result[i] = s.charAt(i);
        }
        return result;
    }

    public static String decodeString(int[] s) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int c : s) {
            stringBuilder.append((char) c);
        }
        return stringBuilder.toString();
    }

    public static TextsLinesEncoding encodeTexts(String[] textA, String[] textB) {
        Map<String, Integer> encodingMap = new HashMap<>(textA.length);
        Map<Integer, String> decodingMap = new HashMap<>(textA.length);

        int[] textAEncoded = new int[textA.length];
        int[] textBEncoded = new int[textB.length];

        int counter = encodeText(Integer.MIN_VALUE, textA, textAEncoded, encodingMap, decodingMap);
        encodeText(counter, textB, textBEncoded, encodingMap, decodingMap);

        return new TextsLinesEncoding(textAEncoded, textBEncoded, decodingMap);
    }

    public static String[] decodeText(int[] text, TextsLinesEncoding encoding) {
        String[] result = new String[text.length];
        for (int i = 0; i < text.length; i++) {
            result[i] = encoding.getLinesDecodingMap().get(text[i]);
        }
        return result;
    }

    private static int encodeText(int counter, String[] text, int[] encodedText,
                                   Map<String, Integer> encodingMap,
                                   Map<Integer, String> decodingMap) {
        for (int i = 0; i < text.length; i++) {
            String s = text[i];
            int c;
            if (!encodingMap.containsKey(s)) {
                c = counter++;
                encodingMap.put(s, c);
                decodingMap.put(c, s);
            } else {
                c = encodingMap.get(s);
            }
            encodedText[i] = c;
        }
        return counter;
    }
}

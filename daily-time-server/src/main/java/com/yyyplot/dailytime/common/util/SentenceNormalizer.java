package com.yyyplot.dailytime.common.util;

import com.ibm.icu.lang.UCharacter;

import java.text.Normalizer;
import java.util.stream.IntStream;

public final class SentenceNormalizer {
    private SentenceNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        // ICU full case-fold 与 Python str.casefold() 对齐（例如 Straße -> strasse）。
        String normalizedText = Normalizer.normalize(value, Normalizer.Form.NFKC);
        String caseFoldedText = UCharacter.foldCase(normalizedText, true);
        StringBuilder withoutPunctuation = new StringBuilder();
        IntStream codePoints = caseFoldedText.codePoints();
        codePoints
                .filter(
                        codePoint -> {
                            int characterType = Character.getType(codePoint);
                            return characterType != Character.CONNECTOR_PUNCTUATION
                                    && characterType != Character.DASH_PUNCTUATION
                                    && characterType != Character.START_PUNCTUATION
                                    && characterType != Character.END_PUNCTUATION
                                    && characterType != Character.INITIAL_QUOTE_PUNCTUATION
                                    && characterType != Character.FINAL_QUOTE_PUNCTUATION
                                    && characterType != Character.OTHER_PUNCTUATION;
                        })
                .forEach(withoutPunctuation::appendCodePoint);
        String trimmedText = withoutPunctuation.toString().trim();
        return trimmedText.replaceAll("\\s+", " ");
    }
}

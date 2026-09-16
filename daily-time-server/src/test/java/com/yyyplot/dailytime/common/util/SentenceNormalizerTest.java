package com.yyyplot.dailytime.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SentenceNormalizerTest {
    @Test
    void matchesPythonCanonicalRules() {
        assertThat(SentenceNormalizer.normalize("  You've， got... some NERVE! "))
                .isEqualTo("youve got some nerve");
    }

    @Test
    void usesFullUnicodeCaseFoldAndNfkc() {
        assertThat(SentenceNormalizer.normalize("Ｓｔｒａße—Test")).isEqualTo("strassetest");
    }

    @Test
    void collapsesUnicodeWhitespace() {
        assertThat(SentenceNormalizer.normalize("A\t\n B")).isEqualTo("a b");
    }
}

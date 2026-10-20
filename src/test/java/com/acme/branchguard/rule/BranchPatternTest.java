package com.acme.branchguard.rule;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BranchPatternTest {

    @Test
    void exactMatch() {
        assertTrue(BranchPattern.matches("main", "main"));
    }

    @Test
    void exactMismatch() {
        assertFalse(BranchPattern.matches("main", "main2"));
    }

    @Test
    void prefixWildcard() {
        assertTrue(BranchPattern.matches("feature/login", "feature/*"));
    }

    @Test
    void wildcardCrossesSlash() {
        assertTrue(BranchPattern.matches("feature/a/b", "feature/*"));
    }

    @Test
    void singleCharWildcard() {
        assertTrue(BranchPattern.matches("dev1", "dev?"));
    }

    @Test
    void dotIsLiteral() {
        assertTrue(BranchPattern.matches("release/2.3", "release/2.3"));
        assertFalse(BranchPattern.matches("release/2x3", "release/2.3"));
    }

    @Test
    void emptyPatternNeverMatches() {
        assertFalse(BranchPattern.matches("main", ""));
        assertFalse(BranchPattern.matches("main", "   "));
        assertFalse(BranchPattern.matches("main", null));
    }

    @Test
    void caseSensitive() {
        assertFalse(BranchPattern.matches("Main", "main"));
    }
}

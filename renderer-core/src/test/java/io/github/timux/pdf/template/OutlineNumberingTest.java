package io.github.timux.pdf.template;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class OutlineNumberingTest {
    @Test void combinesSourcePageAndHeadingLevelsWithoutDuplicateNumbers() {
        var outline = new OutlineNumbering();
        assertEquals("1", outline.next(0));
        assertEquals("1.1", outline.next(1));
        assertEquals("1.1.1", outline.next(2));
        assertEquals("1.2", outline.next(1));
        assertEquals("1.2.1", outline.next(2));
        assertEquals("1.3", outline.next(1));
        assertThrows(IllegalArgumentException.class, () -> outline.next(-1));
    }
}

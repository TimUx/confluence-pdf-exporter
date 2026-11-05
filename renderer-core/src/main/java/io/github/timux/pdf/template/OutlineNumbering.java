package io.github.timux.pdf.template;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

final class OutlineNumbering {
    private final int[] levels = new int[40];
    String next(int level) {
        if (level < 0 || level >= levels.length) throw new IllegalArgumentException("Ungültige Gliederungstiefe.");
        for (int i = 0; i < level; i++) if (levels[i] == 0) levels[i] = 1;
        levels[level]++;
        Arrays.fill(levels, level + 1, levels.length, 0);
        return IntStream.rangeClosed(0, level).mapToObj(i -> Integer.toString(levels[i])).collect(Collectors.joining("."));
    }
}

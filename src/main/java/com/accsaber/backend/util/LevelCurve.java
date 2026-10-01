package com.accsaber.backend.util;

public record LevelCurve(double base, double exponent, int cap) {

    public record Progress(int level, double xpIntoLevel, double xpForNextLevel) {
    }

    public double xpForLevel(int level) {
        if (level <= 0) {
            return 0.0;
        }
        return Math.floor(base * Math.pow(Math.min(level, cap), exponent));
    }

    public double cumulativeXpForLevel(int level) {
        double total = 0.0;
        for (int n = 1; n <= level; n++) {
            total += xpForLevel(n);
        }
        return total;
    }

    public Progress progressAt(double totalXp) {
        int level = 0;
        double cumulative = 0.0;
        double nextCost = xpForLevel(1);
        while (cumulative + nextCost <= totalXp) {
            cumulative += nextCost;
            level++;
            nextCost = xpForLevel(level + 1);
        }
        return new Progress(level, totalXp - cumulative, nextCost);
    }
}

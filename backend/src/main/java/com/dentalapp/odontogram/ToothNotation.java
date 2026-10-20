package com.dentalapp.odontogram;

/** FDI/ISO 3950 two-digit numbering for the 32 permanent and 20 primary teeth. */
public final class ToothNotation {
    private ToothNotation() {}

    public static boolean validFdi(int number) {
        int quadrant = number / 10;
        int position = number % 10;
        return (quadrant >= 1 && quadrant <= 4 && position >= 1 && position <= 8)
            || (quadrant >= 5 && quadrant <= 8 && position >= 1 && position <= 5);
    }

    /** Surface abbreviations; returns false for unknown codes or tooth identifiers. */
    public static boolean validSurface(int number, String surface) {
        if (!validFdi(number)) return false;
        if (surface == null) return true; // whole-tooth observation
        int position = number % 10;
        int quadrant = number / 10;
        return switch (surface) {
            case "M", "D", "B", "L", "F" -> true;
            case "P" -> quadrant == 1 || quadrant == 2 || quadrant == 5 || quadrant == 6;
            case "O" -> position >= 4;
            case "I" -> position <= 3;
            default -> false;
        };
    }
}

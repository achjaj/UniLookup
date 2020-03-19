package com.velitar.unilookup;

/**
 * Unicode symbol representation
 * @author Velitar
 * @version 0.1a
 */
public class Symbol {
    public final String value,
                        name,
                        group,
                        block;
    public final boolean emoji;

    public Symbol(String value, String name, String group, String block, boolean emoji) {
        this.value = value;
        this.name = name;
        this.group = group;
        this.block = block;
        this.emoji = emoji;
    }

    /**
     * Compare two symbols by their name.
     * @param symbol another symbol
     * @return true if name of given symbol equals name of this symbol
     */
    public boolean equalsByName(Symbol symbol) {
        return symbol.name.equals(name);
    }

    /**
     * Get char for current symbol.
     * @return char representation
     */
    public char aChar() {
        return !value.equals("null") ? (char) Integer.parseInt(value, 16) : 0;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Symbol && ((Symbol) obj).value.equals(value);
    }

    @Override
    public String toString() {
        return String.format("{U+%s (%c) %s Block: %s, Group: %s, Emoji: %b}", value, aChar(), name, block, group, emoji);
    }
}

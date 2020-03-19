package com.velitar.unilookup;

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

    public boolean equalsByName(Symbol symbol) {
        return symbol.name.equals(name);
    }

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

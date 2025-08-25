package com.fintech.brokerage.enums;

public enum AssetType {
    TRY("TRY"),
    AAPL("AAPL"),
    TSLA("TSLA"),
    GOOGL("GOOGL");

    private final String symbol;

    AssetType(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }
}

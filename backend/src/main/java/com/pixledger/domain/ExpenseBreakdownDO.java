package com.pixledger.domain;

import java.math.BigDecimal;

public class ExpenseBreakdownDO {
    private String name; private String icon; private BigDecimal amount;
    public String getName() { return name; } public void setName(String name) { this.name = name; }
    public String getIcon() { return icon; } public void setIcon(String icon) { this.icon = icon; }
    public BigDecimal getAmount() { return amount; } public void setAmount(BigDecimal amount) { this.amount = amount; }
}

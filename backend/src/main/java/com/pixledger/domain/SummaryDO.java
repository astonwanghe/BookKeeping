package com.pixledger.domain;

import java.math.BigDecimal;

public class SummaryDO {
    private BigDecimal income; private BigDecimal expense;
    public BigDecimal getIncome() { return income; } public void setIncome(BigDecimal income) { this.income = income; }
    public BigDecimal getExpense() { return expense; } public void setExpense(BigDecimal expense) { this.expense = expense; }
}

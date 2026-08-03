package com.pixledger.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public class BudgetDO {
    private Long id; private Long userId; private LocalDate monthStart; private Long categoryId; private BigDecimal amount;
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; } public void setUserId(Long userId) { this.userId = userId; }
    public LocalDate getMonthStart() { return monthStart; } public void setMonthStart(LocalDate monthStart) { this.monthStart = monthStart; }
    public Long getCategoryId() { return categoryId; } public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public BigDecimal getAmount() { return amount; } public void setAmount(BigDecimal amount) { this.amount = amount; }
}

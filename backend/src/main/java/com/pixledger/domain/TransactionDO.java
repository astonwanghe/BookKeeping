package com.pixledger.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public class TransactionDO {
    private Long id; private Long userId; private Long categoryId; private String type; private BigDecimal amount; private LocalDate occurredOn; private String note; private String categoryName; private String categoryIcon;
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; } public void setUserId(Long userId) { this.userId = userId; }
    public Long getCategoryId() { return categoryId; } public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getType() { return type; } public void setType(String type) { this.type = type; }
    public BigDecimal getAmount() { return amount; } public void setAmount(BigDecimal amount) { this.amount = amount; }
    public LocalDate getOccurredOn() { return occurredOn; } public void setOccurredOn(LocalDate occurredOn) { this.occurredOn = occurredOn; }
    public String getNote() { return note; } public void setNote(String note) { this.note = note; }
    public String getCategoryName() { return categoryName; } public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public String getCategoryIcon() { return categoryIcon; } public void setCategoryIcon(String categoryIcon) { this.categoryIcon = categoryIcon; }
}

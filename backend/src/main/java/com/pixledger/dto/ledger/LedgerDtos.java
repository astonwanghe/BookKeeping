package com.pixledger.dto.ledger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class LedgerDtos {
    private LedgerDtos() {}
    public record CategoryRequest(String name, String type, String icon, Integer sortOrder, Boolean active) {}
    public record CategoryResponse(long id, Long userId, String name, String type, String icon, int sortOrder, boolean active) {}
    public record TransactionRequest(Long categoryId, BigDecimal amount, LocalDate occurredOn, String note) {}
    public record TransactionResponse(long id, long userId, String type, BigDecimal amount, LocalDate occurredOn, String note, long categoryId, String categoryName, String categoryIcon) {}
    public record BudgetRequest(Long categoryId, BigDecimal amount) {}
    public record BudgetResponse(long id, Long categoryId, BigDecimal amount) {}
    public record ExpenseBreakdownResponse(String name, String icon, BigDecimal amount) {}
    public record DashboardResponse(String month, BigDecimal income, BigDecimal expense, BigDecimal balance, List<ExpenseBreakdownResponse> expenseBreakdown, List<BudgetResponse> budgets) {}
}

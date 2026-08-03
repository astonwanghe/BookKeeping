package com.pixledger.api;

import com.pixledger.domain.*;
import com.pixledger.dto.ledger.LedgerDtos.*;
import com.pixledger.service.BudgetService;
import com.pixledger.service.CategoryService;
import com.pixledger.service.TransactionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class LedgerController {

    @Autowired
    CategoryService categoryService;
    @Autowired
    TransactionService transactionService;
    @Autowired
    BudgetService budgetService;

    @GetMapping("/categories")
    List<CategoryResponse> categories(Authentication authentication) {
        return categoryService.list(userId(authentication)).stream()
                .map(this::categoryResponse)
                .toList();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    CategoryResponse addCategory(Authentication authentication, @RequestBody CategoryRequest input) {
        validateCategory(input);
        CategoryDO category = new CategoryDO();
        category.setUserId(userId(authentication));
        category.setName(input.name().trim());
        category.setType(input.type());
        category.setIcon(input.icon() == null ? "square.grid.2x2" : input.icon());
        category.setSortOrder(input.sortOrder() == null ? 999 : input.sortOrder());
        category.setActive(true);
        return categoryResponse(categoryService.create(category));
    }

    @PutMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void editCategory(
            Authentication authentication,
            @PathVariable long id,
            @RequestBody CategoryRequest input
    ) {
        validateCategory(input);
        CategoryDO category = new CategoryDO();
        category.setId(id);
        category.setUserId(userId(authentication));
        category.setName(input.name().trim());
        category.setIcon(input.icon() == null ? "square.grid.2x2" : input.icon());
        category.setSortOrder(input.sortOrder() == null ? 999 : input.sortOrder());
        category.setActive(input.active() == null || input.active());
        if (categoryService.update(category) == 0) {
            throw new IllegalArgumentException("分类不存在或不可修改");
        }
    }

    @GetMapping("/transactions")
    List<TransactionResponse> listTransactions(
            Authentication authentication,
            @RequestParam String month
    ) {
        LocalDate from = monthStart(month);
        return transactionService.list(userId(authentication), from, monthEnd(from)).stream()
                .map(this::transactionResponse)
                .toList();
    }

    @PostMapping("/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    TransactionResponse addTransaction(
            Authentication authentication,
            @RequestBody TransactionRequest input
    ) {
        return transactionResponse(
                transactionService.create(transaction(userId(authentication), null, input))
        );
    }

    @PutMapping("/transactions/{id}")
    TransactionResponse editTransaction(
            Authentication authentication,
            @PathVariable long id,
            @RequestBody TransactionRequest input
    ) {
        TransactionDO transaction = transaction(userId(authentication), id, input);
        if (transactionService.update(transaction) == 0) {
            throw new IllegalArgumentException("流水不存在");
        }
        return transactionResponse(transaction);
    }

    @DeleteMapping("/transactions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteTransaction(Authentication authentication, @PathVariable long id) {
        if (transactionService.delete(userId(authentication), id) == 0) {
            throw new IllegalArgumentException("流水不存在");
        }
    }

    @GetMapping("/dashboard")
    DashboardResponse dashboard(
            Authentication authentication,
            @RequestParam String month
    ) {
        long userId = userId(authentication);
        LocalDate from = monthStart(month);
        LocalDate to = monthEnd(from);
        SummaryDO summary = transactionService.summary(userId, from, to);
        List<ExpenseBreakdownResponse> breakdown = transactionService.expenseBreakdown(userId, from, to)
                .stream()
                .map(item -> new ExpenseBreakdownResponse(item.getName(), item.getIcon(), item.getAmount()))
                .toList();
        List<BudgetResponse> budgetResponses = budgetService.list(userId, from).stream()
                .map(item -> new BudgetResponse(item.getId(), item.getCategoryId(), item.getAmount()))
                .toList();
        return new DashboardResponse(
                month,
                summary.getIncome(),
                summary.getExpense(),
                summary.getIncome().subtract(summary.getExpense()),
                breakdown,
                budgetResponses
        );
    }

    @PutMapping("/budgets/{month}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void upsertBudget(
            Authentication authentication,
            @PathVariable String month,
            @RequestBody BudgetRequest input
    ) {
        validAmount(input.amount());
        BudgetDO budget = new BudgetDO();
        budget.setUserId(userId(authentication));
        budget.setMonthStart(monthStart(month));
        budget.setCategoryId(input.categoryId());
        budget.setAmount(input.amount());
        budgetService.upsert(budget);
    }

    private TransactionDO transaction(long userId, Long id, TransactionRequest input) {
        validAmount(input.amount());
        if (input.categoryId() == null) {
            throw new IllegalArgumentException("请选择分类");
        }
        CategoryDO category = categoryService.owned(userId, input.categoryId());
        if (category == null) {
            throw new IllegalArgumentException("分类不可用");
        }
        TransactionDO transaction = new TransactionDO();
        transaction.setId(id);
        transaction.setUserId(userId);
        transaction.setCategoryId(input.categoryId());
        transaction.setType(category.getType());
        transaction.setAmount(input.amount());
        transaction.setOccurredOn(input.occurredOn() == null ? LocalDate.now() : input.occurredOn());
        transaction.setNote(input.note());
        transaction.setCategoryName(category.getName());
        transaction.setCategoryIcon(category.getIcon());
        return transaction;
    }

    private LocalDate monthStart(String month) {
        try {
            return YearMonth.parse(month).atDay(1);
        } catch (Exception exception) {
            throw new IllegalArgumentException("月份格式应为 YYYY-MM");
        }
    }

    private LocalDate monthEnd(LocalDate monthStart) {
        return monthStart.plusMonths(1).minusDays(1);
    }

    private void validAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2) {
            throw new IllegalArgumentException("金额必须为最多两位小数的正数");
        }
    }

    private void validateCategory(CategoryRequest input) {
        if (input.name() == null
                || input.name().isBlank()
                || !("INCOME".equals(input.type()) || "EXPENSE".equals(input.type()))) {
            throw new IllegalArgumentException("分类信息不正确");
        }
    }

    private CategoryResponse categoryResponse(CategoryDO category) {
        return new CategoryResponse(
                category.getId(),
                category.getUserId(),
                category.getName(),
                category.getType(),
                category.getIcon(),
                category.getSortOrder(),
                Boolean.TRUE.equals(category.getActive())
        );
    }

    private TransactionResponse transactionResponse(TransactionDO transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getUserId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getOccurredOn(),
                transaction.getNote(),
                transaction.getCategoryId(),
                transaction.getCategoryName(),
                transaction.getCategoryIcon()
        );
    }

    private long userId(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }
}

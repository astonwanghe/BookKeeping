package com.pixledger.api;

import com.pixledger.domain.*;
import com.pixledger.dto.ledger.LedgerDtos.*;
import com.pixledger.mapper.LedgerMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class LedgerController {
    private final LedgerMapper mapper;
    public LedgerController(LedgerMapper mapper) { this.mapper = mapper; }

    @GetMapping("/categories")
    List<CategoryResponse> categories(Authentication authentication) {
        return mapper.categories(user(authentication)).stream().map(this::categoryResponse).toList();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    CategoryResponse addCategory(Authentication authentication, @RequestBody CategoryRequest input) {
        validateCategory(input);
        CategoryDO category = new CategoryDO();
        category.setUserId(user(authentication)); category.setName(input.name().trim()); category.setType(input.type());
        category.setIcon(input.icon() == null ? "square.grid.2x2" : input.icon());
        category.setSortOrder(input.sortOrder() == null ? 999 : input.sortOrder()); category.setActive(true);
        mapper.createCategory(category);
        return categoryResponse(category);
    }

    @PutMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void editCategory(Authentication authentication, @PathVariable long id, @RequestBody CategoryRequest input) {
        validateCategory(input);
        CategoryDO category = new CategoryDO();
        category.setId(id); category.setUserId(user(authentication)); category.setName(input.name().trim());
        category.setIcon(input.icon() == null ? "square.grid.2x2" : input.icon());
        category.setSortOrder(input.sortOrder() == null ? 999 : input.sortOrder());
        category.setActive(input.active() == null || input.active());
        if (mapper.updateCategory(category) == 0) throw new IllegalArgumentException("分类不存在或不可修改");
    }

    @GetMapping("/transactions")
    List<TransactionResponse> list(Authentication authentication, @RequestParam String month) {
        LocalDate start = start(month);
        return mapper.transactions(user(authentication), start, start.plusMonths(1).minusDays(1)).stream().map(this::transactionResponse).toList();
    }

    @PostMapping("/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    TransactionResponse add(Authentication authentication, @RequestBody TransactionRequest input) { return save(authentication, null, input); }

    @PutMapping("/transactions/{id}")
    TransactionResponse edit(Authentication authentication, @PathVariable long id, @RequestBody TransactionRequest input) { return save(authentication, id, input); }

    @DeleteMapping("/transactions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(Authentication authentication, @PathVariable long id) {
        if (mapper.deleteTransaction(user(authentication), id) == 0) throw new IllegalArgumentException("流水不存在");
    }

    @GetMapping("/dashboard")
    DashboardResponse dashboard(Authentication authentication, @RequestParam String month) {
        LocalDate start = start(month); LocalDate end = start.plusMonths(1).minusDays(1); long userId = user(authentication);
        SummaryDO summary = mapper.summary(userId, start, end);
        List<ExpenseBreakdownResponse> breakdown = mapper.expenseBreakdown(userId, start, end).stream().map(item -> new ExpenseBreakdownResponse(item.getName(), item.getIcon(), item.getAmount())).toList();
        List<BudgetResponse> budgets = mapper.budgets(userId, start).stream().map(item -> new BudgetResponse(item.getId(), item.getCategoryId(), item.getAmount())).toList();
        return new DashboardResponse(month, summary.getIncome(), summary.getExpense(), summary.getIncome().subtract(summary.getExpense()), breakdown, budgets);
    }

    @PutMapping("/budgets/{month}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void budget(Authentication authentication, @PathVariable String month, @RequestBody BudgetRequest input) {
        validAmount(input.amount());
        BudgetDO budget = new BudgetDO(); budget.setUserId(user(authentication)); budget.setMonthStart(start(month)); budget.setCategoryId(input.categoryId()); budget.setAmount(input.amount());
        mapper.upsertBudget(budget);
    }

    private TransactionResponse save(Authentication authentication, Long id, TransactionRequest input) {
        validAmount(input.amount());
        if (input.categoryId() == null) throw new IllegalArgumentException("请选择分类");
        long userId = user(authentication); CategoryDO category = mapper.ownedCategory(userId, input.categoryId());
        if (category == null) throw new IllegalArgumentException("分类不可用");
        TransactionDO transaction = new TransactionDO(); transaction.setId(id); transaction.setUserId(userId); transaction.setCategoryId(input.categoryId()); transaction.setType(category.getType());
        transaction.setAmount(input.amount()); transaction.setOccurredOn(input.occurredOn() == null ? LocalDate.now() : input.occurredOn()); transaction.setNote(input.note());
        transaction.setCategoryName(category.getName()); transaction.setCategoryIcon(category.getIcon());
        if (id == null) mapper.createTransaction(transaction); else if (mapper.updateTransaction(transaction) == 0) throw new IllegalArgumentException("流水不存在");
        return transactionResponse(transaction);
    }

    private long user(Authentication authentication) { return (Long) authentication.getPrincipal(); }
    private void validAmount(BigDecimal amount) { if (amount == null || amount.signum() <= 0 || amount.scale() > 2) throw new IllegalArgumentException("金额必须为最多两位小数的正数"); }
    private LocalDate start(String month) { try { return YearMonth.parse(month).atDay(1); } catch (Exception exception) { throw new IllegalArgumentException("月份格式应为 YYYY-MM"); } }
    private void validateCategory(CategoryRequest input) { if (input.name() == null || input.name().isBlank() || !("INCOME".equals(input.type()) || "EXPENSE".equals(input.type()))) throw new IllegalArgumentException("分类信息不正确"); }
    private CategoryResponse categoryResponse(CategoryDO category) { return new CategoryResponse(category.getId(), category.getUserId(), category.getName(), category.getType(), category.getIcon(), category.getSortOrder(), Boolean.TRUE.equals(category.getActive())); }
    private TransactionResponse transactionResponse(TransactionDO transaction) { return new TransactionResponse(transaction.getId(), transaction.getUserId(), transaction.getType(), transaction.getAmount(), transaction.getOccurredOn(), transaction.getNote(), transaction.getCategoryId(), transaction.getCategoryName(), transaction.getCategoryIcon()); }
}

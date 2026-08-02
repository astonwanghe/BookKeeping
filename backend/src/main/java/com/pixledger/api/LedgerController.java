package com.pixledger.api;

import com.pixledger.mapper.LedgerMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class LedgerController {
    private final LedgerMapper mapper;

    public LedgerController(LedgerMapper mapper) {
        this.mapper = mapper;
    }

    record Category(String name, String type, String icon, Integer sortOrder, Boolean active) {
    }

    record Transaction(Long categoryId, BigDecimal amount, LocalDate occurredOn, String note) {
    }

    record Budget(Long categoryId, BigDecimal amount) {
    }

    private long user(Authentication a) {
        return (Long) a.getPrincipal();
    }

    private void validAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2)
            throw new IllegalArgumentException("金额必须为最多两位小数的正数");
    }

    private LocalDate start(String month) {
        try {
            return YearMonth.parse(month).atDay(1);
        } catch (Exception e) {
            throw new IllegalArgumentException("月份格式应为 YYYY-MM");
        }
    }

    @GetMapping("/categories")
    List<Map<String, Object>> categories(Authentication a) {
        return mapper.categories(user(a));
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> addCategory(Authentication a, @RequestBody Category in) {
        if (in.name() == null || in.name().isBlank() || !("INCOME".equals(in.type()) || "EXPENSE".equals(in.type())))
            throw new IllegalArgumentException("分类信息不正确");
        var c = new HashMap<String, Object>();
        c.put("userId", user(a));
        c.put("name", in.name().trim());
        c.put("type", in.type());
        c.put("icon", Optional.ofNullable(in.icon()).orElse("square.grid.2x2"));
        c.put("sortOrder", Optional.ofNullable(in.sortOrder()).orElse(999));
        mapper.createCategory(c);
        return c;
    }

    @PutMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void editCategory(Authentication a, @PathVariable long id, @RequestBody Category in) {
        var c = new HashMap<String, Object>();
        c.put("id", id);
        c.put("userId", user(a));
        c.put("name", in.name());
        c.put("icon", in.icon());
        c.put("sortOrder", in.sortOrder());
        c.put("active", in.active());
        if (mapper.updateCategory(c) == 0) throw new IllegalArgumentException("分类不存在或不可修改");
    }

    @GetMapping("/transactions")
    List<Map<String, Object>> list(Authentication a, @RequestParam String month) {
        var s = start(month);
        return mapper.transactions(user(a), s, s.plusMonths(1).minusDays(1));
    }

    @PostMapping("/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> add(Authentication a, @RequestBody Transaction in) {
        return save(a, null, in);
    }

    @PutMapping("/transactions/{id}")
    Map<String, Object> edit(Authentication a, @PathVariable long id, @RequestBody Transaction in) {
        return save(a, id, in);
    }

    @DeleteMapping("/transactions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(Authentication a, @PathVariable long id) {
        if (mapper.deleteTransaction(user(a), id) == 0) throw new IllegalArgumentException("流水不存在");
    }

    private Map<String, Object> save(Authentication a, Long id, Transaction in) {
        long uid = user(a);
        validAmount(in.amount());
        var category = mapper.ownedCategory(uid, in.categoryId());
        if (category == null) throw new IllegalArgumentException("分类不可用");
        var tx = new HashMap<String, Object>();
        tx.put("id", id);
        tx.put("userId", uid);
        tx.put("categoryId", in.categoryId());
        tx.put("type", category.get("type"));
        tx.put("amount", in.amount());
        tx.put("occurredOn", Optional.ofNullable(in.occurredOn()).orElse(LocalDate.now()));
        tx.put("note", in.note());
        if (id == null) mapper.createTransaction(tx);
        else if (mapper.updateTransaction(tx) == 0) throw new IllegalArgumentException("流水不存在");
        return tx;
    }

    @GetMapping("/dashboard")
    Map<String, Object> dashboard(Authentication a, @RequestParam String month) {
        var s = start(month);
        var e = s.plusMonths(1).minusDays(1);
        var totals = mapper.summary(user(a), s, e);
        return Map.of("month", month, "income", totals.get("income"), "expense", totals.get("expense"), "balance", totals.get("income").subtract(totals.get("expense")), "expenseBreakdown", mapper.expenseBreakdown(user(a), s, e), "budgets", mapper.budgets(user(a), s));
    }

    @PutMapping("/budgets/{month}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void budget(Authentication a, @PathVariable String month, @RequestBody Budget in) {
        validAmount(in.amount());
        var b = new HashMap<String, Object>();
        b.put("userId", user(a));
        b.put("monthStart", start(month));
        b.put("categoryId", in.categoryId());
        b.put("amount", in.amount());
        mapper.upsertBudget(b);
    }
}

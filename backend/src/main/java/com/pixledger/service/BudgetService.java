package com.pixledger.service;

import com.pixledger.domain.BudgetDO;
import com.pixledger.mapper.LedgerMapper;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class BudgetService {
    private final LedgerMapper mapper;

    public BudgetService(LedgerMapper mapper) {
        this.mapper = mapper;
    }

    public List<BudgetDO> list(long userId, LocalDate monthStart) {
        return mapper.budgets(userId, monthStart);
    }

    public void upsert(BudgetDO budget) {
        mapper.upsertBudget(budget);
    }
}

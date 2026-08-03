package com.pixledger.service;

import com.pixledger.domain.ExpenseBreakdownDO;
import com.pixledger.domain.SummaryDO;
import com.pixledger.domain.TransactionDO;
import com.pixledger.mapper.LedgerMapper;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TransactionService {
    private final LedgerMapper mapper;

    public TransactionService(LedgerMapper mapper) {
        this.mapper = mapper;
    }

    public List<TransactionDO> list(long userId, LocalDate from, LocalDate to) {
        return mapper.transactions(userId, from, to);
    }

    public TransactionDO create(TransactionDO transaction) {
        mapper.createTransaction(transaction);
        return transaction;
    }

    public int update(TransactionDO transaction) {
        return mapper.updateTransaction(transaction);
    }

    public int delete(long userId, long transactionId) {
        return mapper.deleteTransaction(userId, transactionId);
    }

    public SummaryDO summary(long userId, LocalDate from, LocalDate to) {
        return mapper.summary(userId, from, to);
    }

    public List<ExpenseBreakdownDO> expenseBreakdown(long userId, LocalDate from, LocalDate to) {
        return mapper.expenseBreakdown(userId, from, to);
    }
}

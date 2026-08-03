package com.pixledger.service;

import com.pixledger.domain.CategoryDO;
import com.pixledger.mapper.LedgerMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CategoryService {
    private final LedgerMapper mapper;

    public CategoryService(LedgerMapper mapper) {
        this.mapper = mapper;
    }

    public List<CategoryDO> list(long userId) {
        return mapper.categories(userId);
    }

    public CategoryDO owned(long userId, long categoryId) {
        return mapper.ownedCategory(userId, categoryId);
    }

    public CategoryDO create(CategoryDO category) {
        mapper.createCategory(category);
        return category;
    }

    public int update(CategoryDO category) {
        return mapper.updateCategory(category);
    }
}

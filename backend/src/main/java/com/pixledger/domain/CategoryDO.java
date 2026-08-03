package com.pixledger.domain;

public class CategoryDO {
    private Long id; private Long userId; private String name; private String type; private String icon; private Integer sortOrder; private Boolean active;
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; } public void setUserId(Long userId) { this.userId = userId; }
    public String getName() { return name; } public void setName(String name) { this.name = name; }
    public String getType() { return type; } public void setType(String type) { this.type = type; }
    public String getIcon() { return icon; } public void setIcon(String icon) { this.icon = icon; }
    public Integer getSortOrder() { return sortOrder; } public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Boolean getActive() { return active; } public void setActive(Boolean active) { this.active = active; }
}

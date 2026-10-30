package cn.herb.model;

public enum Role {
    ADMIN("管理员"), PHARMACIST("药师"), GUEST("访客");
    private final String label;
    Role(String label) { this.label = label; }
    public boolean canEdit() { return this != GUEST; }
    @Override public String toString() { return label; }
}

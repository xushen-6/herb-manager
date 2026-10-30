package cn.herb.model;

import java.math.BigDecimal;

public record Medicinal(long id, String code, String name, String category, String origin,
                        int stock, BigDecimal price, String efficacy, String usage,
                        String storage, int threshold) {
    public boolean lowStock() { return stock < threshold; }
    public MedicinalDetails details() { return new MedicinalDetails(efficacy, usage, storage); }
}

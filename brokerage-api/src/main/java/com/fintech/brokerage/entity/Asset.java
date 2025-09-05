package com.fintech.brokerage.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "asset", uniqueConstraints = @UniqueConstraint(columnNames = {"customer_id", "asset_name"}))
public class Asset {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customerId;//The API requirements specified that customer information should be 
    							//kept under the name customerId, so it was named that way.

    @Column(name = "asset_name", nullable = false)
    private String assetName;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal size;

    @Column(name = "usable_size", nullable = false, precision = 19, scale = 4)
    private BigDecimal usableSize;

    @Version
    private long version; // optimistic locking for concurrent balance adjustments
    
    protected Asset() {}

    public Asset(Customer customerId, String assetName, BigDecimal size, BigDecimal usableSize) {
        this.customerId = customerId;
        this.assetName = assetName;
        this.size = size;
        this.usableSize = usableSize;
    }

    public UUID getId() { return id; }
    public Customer getCustomerId() { return customerId; }
    public String getAssetName() { return assetName; }
    public BigDecimal getSize() { return size; }
    public BigDecimal getUsableSize() { return usableSize; }

    public void setSize(BigDecimal size) { this.size = size; }
    public void setUsableSize(BigDecimal usableSize) { this.usableSize = usableSize; }
    
    // helper methods to keep arithmetic consistent
    public void addToUsable(BigDecimal delta) { this.usableSize = this.usableSize.add(delta); }
    public void subFromUsable(BigDecimal delta) { this.usableSize = this.usableSize.subtract(delta); }
    public void addToTotal(BigDecimal delta) { this.size = this.size.add(delta); }
    public void subFromTotal(BigDecimal delta) { this.size = this.size.subtract(delta); }
}

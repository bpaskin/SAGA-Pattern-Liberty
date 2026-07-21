package com.saga.transfer;

public class TransferRequest {
    private String fromAccount;
    private String toAccount;
    private java.math.BigDecimal amount;

    public TransferRequest() {}

    public String getFromAccount() { return fromAccount; }
    public void setFromAccount(String fromAccount) { this.fromAccount = fromAccount; }

    public String getToAccount() { return toAccount; }
    public void setToAccount(String toAccount) { this.toAccount = toAccount; }

    public java.math.BigDecimal getAmount() { return amount; }
    public void setAmount(java.math.BigDecimal amount) { this.amount = amount; }
}

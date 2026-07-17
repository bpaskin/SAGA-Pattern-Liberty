package com.saga.withdrawal;

import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@ApplicationScoped
public class AccountRepository {

    @Resource(lookup = "jdbc/AccountDS")
    private DataSource dataSource;

    public void withdraw(String accountNumber, BigDecimal amount) throws SQLException {
        // Check sufficient funds first
        BigDecimal balance = getBalance(accountNumber);
        if (balance.compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient funds. Balance: " + balance + ", Requested: " + amount);
        }
        String sql = "UPDATE accounts SET balance = balance - ? WHERE account_number = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, amount);
            ps.setString(2, accountNumber);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new IllegalArgumentException("Account not found: " + accountNumber);
            }
        }
    }

    public void reverseWithdrawal(String accountNumber, BigDecimal amount) throws SQLException {
        String sql = "UPDATE accounts SET balance = balance + ? WHERE account_number = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, amount);
            ps.setString(2, accountNumber);
            ps.executeUpdate();
        }
    }

    public BigDecimal getBalance(String accountNumber) throws SQLException {
        String sql = "SELECT balance FROM accounts WHERE account_number = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accountNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal("balance");
                }
                throw new IllegalArgumentException("Account not found: " + accountNumber);
            }
        }
    }
}

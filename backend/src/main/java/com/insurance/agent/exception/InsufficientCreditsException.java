package com.insurance.agent.exception;

public class InsufficientCreditsException extends RuntimeException {
    private final int balance;
    private final int required;

    public InsufficientCreditsException(int balance, int required) {
        super(String.format("积分不足（当前 %d 积分，需要 %d 积分）", balance, required));
        this.balance = balance;
        this.required = required;
    }

    public int getBalance() { return balance; }
    public int getRequired() { return required; }
}

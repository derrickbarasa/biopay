package com.flexmoney.nca.objects;

public class Payment {
    String transId, amount,  householdNumber,enrolmentNumber, householdName, status;

    public Payment(String transId, String amount,  String householdNumber, String enrolmentNumber, String householdName, String status) {
        this.transId = transId;
        this.amount = amount;

        this.householdNumber = householdNumber;
        this.enrolmentNumber = enrolmentNumber;
        this.householdName = householdName;
        this.status = status;
    }

    public String getTransId() {
        return transId;
    }

    public void setTransId(String transId) {
        this.transId = transId;
    }

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public String getHouseholdNumber() {
        return householdNumber;
    }

    public void setHouseholdNumber(String householdNumber) {
        this.householdNumber = householdNumber;
    }

    public String getEnrolmentNumber() {
        return enrolmentNumber;
    }

    public void setEnrolmentNumber(String enrolmentNumber) {
        this.enrolmentNumber = enrolmentNumber;
    }

    public String getHouseholdName() {
        return householdName;
    }

    public void setHouseholdName(String householdName) {
        this.householdName = householdName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

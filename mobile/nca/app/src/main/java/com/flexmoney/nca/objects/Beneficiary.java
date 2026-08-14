package com.flexmoney.nca.objects;

public class Beneficiary {
    String beneficiaryName, beneficiaryNumber;
    int level;

    public Beneficiary(String beneficiaryName, String beneficiaryNumber, int level) {
        this.beneficiaryName = beneficiaryName;
        this.beneficiaryNumber = beneficiaryNumber;
        this.level = level;
    }

    public String getBeneficiaryName() {
        return beneficiaryName;
    }

    public void setBeneficiaryName(String beneficiaryName) {
        this.beneficiaryName = beneficiaryName;
    }

    public String getBeneficiaryNumber() {
        return beneficiaryNumber;
    }

    public void setBeneficiaryNumber(String beneficiaryNumber) {
        this.beneficiaryNumber = beneficiaryNumber;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }
}

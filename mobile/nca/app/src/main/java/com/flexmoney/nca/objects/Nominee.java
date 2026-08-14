package com.flexmoney.nca.objects;

public class Nominee {
    String nomineeName, nomineeAge, nomineeGender, nomineeRelationship, nomineeOccupation,nomineeOtherOccupation;

    public Nominee(String nomineeName, String nomineeAge, String nomineeGender, String nomineeRelationship,String nomineeOccupation,String nomineeOtherOccupation) {
        this.nomineeName = nomineeName;
        this.nomineeAge = nomineeAge;
        this.nomineeGender = nomineeGender;
        this.nomineeRelationship = nomineeRelationship;
        this.nomineeOccupation = nomineeOccupation;
        this.nomineeOtherOccupation = nomineeOtherOccupation;
    }

    public String getNomineeName() {
        return nomineeName;
    }

    public void setNomineeName(String nomineeName) {
        this.nomineeName = nomineeName;
    }

    public String getNomineeAge() {
        return nomineeAge;
    }

    public void setNomineeAge(String nomineeAge) {
        this.nomineeAge = nomineeAge;
    }

    public String getNomineeGender() {
        return nomineeGender;
    }

    public void setNomineeGender(String nomineeGender) {
        this.nomineeGender = nomineeGender;
    }

    public String getNomineeRelationship() {
        return nomineeRelationship;
    }

    public void setNomineeRelationship(String nomineeRelationship) {
        this.nomineeRelationship = nomineeRelationship;
    }

    public String getNomineeOccupation() {
        return nomineeOccupation;
    }

    public void setNomineeOccupation(String nomineeOccupation) {
        this.nomineeOccupation = nomineeOccupation;
    }

    public String getNomineeOtherOccupation() {
        return nomineeOtherOccupation;
    }

    public void setNomineeOtherOccupation(String nomineeOtherOccupation) {
        this.nomineeOtherOccupation = nomineeOtherOccupation;
    }
}

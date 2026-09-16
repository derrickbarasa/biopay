-- Adds "Host community" as a legal status option (see HouseholdClassification.java /
-- householdClassifications.ts). The CHECK constraint from 037_household_classifications.sql
-- has to be dropped and recreated to include the new code -- SQL Server has no ALTER CHECK.

IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_households_legal_status')
    ALTER TABLE households DROP CONSTRAINT CK_households_legal_status;
GO

ALTER TABLE households WITH NOCHECK ADD CONSTRAINT CK_households_legal_status
    CHECK (legal_status IS NULL OR legal_status IN
        ('CITIZEN','REFUGEE','IDP','ASYLUM_SEEKER','RETURNEE','HOST_COMMUNITY','STATELESS','OTHER'));
GO

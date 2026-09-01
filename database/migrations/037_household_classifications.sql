-- Controlled household classifications.
-- Vulnerability is a comma-separated set of server-validated codes because a household
-- may have more than one support need. Legal status remains a single controlled code.
-- Existing NULL values remain untouched and continue to display as "Not recorded".

IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('households') AND name = 'vulnerability_status')
    ALTER TABLE households ALTER COLUMN vulnerability_status VARCHAR(255) NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_households_legal_status')
    ALTER TABLE households WITH NOCHECK ADD CONSTRAINT CK_households_legal_status
        CHECK (legal_status IS NULL OR legal_status IN
            ('CITIZEN','REFUGEE','IDP','ASYLUM_SEEKER','RETURNEE','STATELESS','OTHER'));
GO

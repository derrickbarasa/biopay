-- Face-recognition enrolment records. Embeddings are application-encrypted before storage.
-- This schema intentionally stores model/version metadata because embeddings from different
-- recognition models are not comparable.
IF OBJECT_ID('dbo.faces', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.faces (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        supervisor_id VARCHAR(100) NULL,
        beneficiary_type INT NOT NULL,
        beneficiary_id VARCHAR(100) NOT NULL,
        uuid VARCHAR(100) NOT NULL,
        embedding VARCHAR(MAX) NOT NULL,
        embedding_dimensions INT NOT NULL,
        model_version VARCHAR(100) NOT NULL,
        quality_score DECIMAL(8,6) NULL,
        partner_code VARCHAR(100) NOT NULL,
        status INT NOT NULL CONSTRAINT DF_faces_status DEFAULT 1,
        created_by VARCHAR(100) NULL,
        created_at DATETIME2 NOT NULL CONSTRAINT DF_faces_created_at DEFAULT SYSUTCDATETIME(),
        stored_at DATETIME2 NOT NULL CONSTRAINT DF_faces_stored_at DEFAULT SYSUTCDATETIME(),
        CONSTRAINT UQ_faces_uuid UNIQUE (uuid)
    );

    CREATE INDEX IX_faces_partner_beneficiary
        ON dbo.faces(partner_code, beneficiary_id, beneficiary_type, status);
END;

IF COL_LENGTH('dbo.households', 'registration_method') IS NULL
    ALTER TABLE dbo.households ADD registration_method VARCHAR(30) NULL;

IF COL_LENGTH('dbo.alternates', 'registration_method') IS NULL
    ALTER TABLE dbo.alternates ADD registration_method VARCHAR(30) NULL;

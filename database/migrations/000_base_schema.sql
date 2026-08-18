-- 000_base_schema.sql
-- Complete fresh-install foundation for BioPay. Later numbered migrations remain
-- idempotent and extend these tables. No foreign keys are declared, preserving the
-- project's code/id based integration convention.

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'partners')
BEGIN
    CREATE TABLE partners (
        id INT IDENTITY(1,1) PRIMARY KEY,
        partner_id VARCHAR(20) NOT NULL UNIQUE,
        name VARCHAR(150) NOT NULL,
        types VARCHAR(30) NULL,
        authorised_name VARCHAR(150) NULL,
        authorised_email VARCHAR(150) NULL,
        authorised_contact VARCHAR(50) NULL,
        address VARCHAR(255) NULL,
        created_at DATETIME NOT NULL DEFAULT GETDATE(),
        updated_at DATETIME NULL
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'roles')
BEGIN
    CREATE TABLE roles (
        id INT IDENTITY(1,1) PRIMARY KEY,
        role_name VARCHAR(80) NOT NULL UNIQUE,
        description VARCHAR(255) NULL,
        anchor_id INT NULL,
        partner_code VARCHAR(20) NULL,
        role_scope VARCHAR(20) NOT NULL DEFAULT 'ORGANISATION',
        status INT NOT NULL DEFAULT 1,
        created_at DATETIME NOT NULL DEFAULT GETDATE(),
        updated_at DATETIME NULL
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'permissions')
BEGIN
    CREATE TABLE permissions (
        id INT IDENTITY(1,1) PRIMARY KEY,
        permission_name VARCHAR(100) NOT NULL UNIQUE,
        description VARCHAR(255) NULL,
        created_at DATETIME NOT NULL DEFAULT GETDATE()
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'role_permissions')
BEGIN
    CREATE TABLE role_permissions (
        role_id INT NOT NULL,
        permission_id INT NOT NULL,
        status BIT NOT NULL DEFAULT 1,
        created_at DATETIME NOT NULL DEFAULT GETDATE(),
        CONSTRAINT UQ_role_permissions UNIQUE (role_id, permission_id)
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'users')
BEGIN
    CREATE TABLE users (
        id INT IDENTITY(1,1) PRIMARY KEY,
        partner_code VARCHAR(20) NULL,
        email VARCHAR(150) NOT NULL UNIQUE,
        username VARCHAR(100) NOT NULL UNIQUE,
        password VARCHAR(255) NOT NULL,
        first_name VARCHAR(100) NULL,
        other_names VARCHAR(150) NULL,
        role_id INT NULL,
        active BIT NOT NULL DEFAULT 1,
        status INT NOT NULL DEFAULT 1,
        created_by INT NULL,
        created_at DATETIME NOT NULL DEFAULT GETDATE(),
        updated_at DATETIME NULL
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'supervisors')
BEGIN
    CREATE TABLE supervisors (
        id INT IDENTITY(1,1) PRIMARY KEY,
        supervisor_id INT NULL,
        username VARCHAR(100) NOT NULL UNIQUE,
        email VARCHAR(150) NOT NULL UNIQUE,
        password VARCHAR(255) NOT NULL,
        firstname VARCHAR(100) NULL,
        lastname VARCHAR(100) NULL,
        partner_code VARCHAR(20) NULL,
        role INT NULL,
        active VARCHAR(5) NOT NULL DEFAULT '1',
        created_by INT NULL,
        created_at DATETIME NOT NULL DEFAULT GETDATE()
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'households')
BEGIN
    CREATE TABLE households (
        id INT IDENTITY(1,1) PRIMARY KEY,
        supervisor_id VARCHAR(100) NULL,
        partner_code VARCHAR(20) NOT NULL,
        household_number VARCHAR(100) NOT NULL UNIQUE,
        beneficiary_type VARCHAR(20) NULL,
        household_name VARCHAR(200) NOT NULL,
        age INT NULL,
        marital_status VARCHAR(50) NULL,
        spouse_name VARCHAR(150) NULL,
        id_number VARCHAR(100) NULL,
        phone_number VARCHAR(50) NULL,
        gender VARCHAR(20) NULL,
        household_size INT NULL,
        female_dependants INT NULL,
        male_dependants INT NULL,
        state_code VARCHAR(20) NULL,
        county_code VARCHAR(20) NULL,
        payam_code VARCHAR(20) NULL,
        boma_code VARCHAR(20) NULL,
        latitude VARCHAR(50) NULL,
        longitude VARCHAR(50) NULL,
        duplicate INT NOT NULL DEFAULT 0,
        duplicate_number VARCHAR(100) NULL,
        matching_score VARCHAR(30) NULL,
        status INT NOT NULL DEFAULT 1,
        stored_at DATETIME NULL,
        created_at DATETIME NOT NULL DEFAULT GETDATE(),
        updated_at DATETIME NULL
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'alternates')
BEGIN
    CREATE TABLE alternates (
        id INT IDENTITY(1,1) PRIMARY KEY,
        supervisor_id VARCHAR(100) NULL,
        household_number VARCHAR(100) NOT NULL,
        alternate_number VARCHAR(100) NOT NULL UNIQUE,
        alternate_name VARCHAR(200) NOT NULL,
        relationship VARCHAR(80) NULL,
        alternate_rank INT NULL,
        age INT NULL,
        id_number VARCHAR(100) NULL,
        phone_number VARCHAR(50) NULL,
        gender VARCHAR(20) NULL,
        duplicate INT NOT NULL DEFAULT 0,
        duplicate_number VARCHAR(100) NULL,
        matching_score VARCHAR(30) NULL,
        status INT NOT NULL DEFAULT 1,
        stored_at DATETIME NULL,
        created_at DATETIME NOT NULL DEFAULT GETDATE(),
        updated_at DATETIME NULL
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'fingerprints')
BEGIN
    CREATE TABLE fingerprints (
        id INT IDENTITY(1,1) PRIMARY KEY,
        supervisor_id VARCHAR(100) NULL,
        beneficiary_type INT NOT NULL,
        beneficiary_id VARCHAR(100) NOT NULL,
        fingerprint_number INT NOT NULL,
        uuid VARCHAR(100) NOT NULL UNIQUE,
        fingerprint VARCHAR(MAX) NOT NULL,
        status INT NOT NULL DEFAULT 1,
        stored_at DATETIME NULL,
        created_at DATETIME NOT NULL DEFAULT GETDATE()
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'images')
BEGIN
    CREATE TABLE images (
        id INT IDENTITY(1,1) PRIMARY KEY,
        supervisor_id VARCHAR(100) NULL,
        beneficiary_type INT NOT NULL,
        beneficiary_id VARCHAR(100) NOT NULL,
        photo_url VARCHAR(500) NOT NULL,
        status INT NOT NULL DEFAULT 1,
        stored_at DATETIME NULL,
        created_at DATETIME NOT NULL DEFAULT GETDATE()
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'payments')
BEGIN
    CREATE TABLE payments (
        id INT IDENTITY(1,1) PRIMARY KEY,
        supervisor_id VARCHAR(100) NULL,
        household_number VARCHAR(100) NOT NULL,
        household_name VARCHAR(200) NULL,
        gender VARCHAR(20) NULL,
        boma_code VARCHAR(20) NULL,
        amount NUMERIC(18,2) NOT NULL DEFAULT 0,
        cycle VARCHAR(30) NULL,
        payment_cycle VARCHAR(30) NULL,
        date_from DATE NULL,
        date_to DATE NULL,
        attendance NUMERIC(18,2) NULL,
        wage NUMERIC(18,2) NULL,
        matched_fp VARCHAR(100) NULL,
        latitude VARCHAR(50) NULL,
        longitude VARCHAR(50) NULL,
        uuid VARCHAR(100) NOT NULL UNIQUE,
        status INT NOT NULL DEFAULT 0,
        approved BIT NOT NULL DEFAULT 0,
        approved_by INT NULL,
        approved_at DATETIME NULL,
        verified_by INT NULL,
        verified_at DATETIME NULL,
        created_by INT NULL,
        created_at DATETIME NOT NULL DEFAULT GETDATE(),
        updated_at DATETIME NULL
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'attendances')
BEGIN
    CREATE TABLE attendances (
        id INT IDENTITY(1,1) PRIMARY KEY,
        supervisor_id VARCHAR(100) NULL,
        household_number VARCHAR(100) NOT NULL,
        beneficiary_type INT NULL,
        beneficiary_id VARCHAR(100) NULL,
        matched_fp VARCHAR(100) NULL,
        uuid VARCHAR(100) NOT NULL UNIQUE,
        clock VARCHAR(5) NULL,
        time DATETIME NULL,
        attendance_date DATE NULL,
        status INT NOT NULL DEFAULT 1,
        created_at DATETIME NOT NULL DEFAULT GETDATE()
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'audits')
BEGIN
    CREATE TABLE audits (
        id INT IDENTITY(1,1) PRIMARY KEY,
        user_id INT NULL,
        activity VARCHAR(255) NULL,
        ip_address VARCHAR(50) NULL,
        created_at DATETIME NOT NULL DEFAULT GETDATE()
    );
END
GO

-- Roles
CREATE TABLE roles (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(20) NOT NULL UNIQUE
);

INSERT INTO roles (name) VALUES ('CUSTOMER'), ('ADMIN');

-- Users
CREATE TABLE users (
    id              UUID PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    full_name       VARCHAR(100) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    role_id         BIGINT NOT NULL REFERENCES roles(id),
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_email ON users(email);

-- Folders (self-referential, nested)
CREATE TABLE folders (
    id          UUID PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    owner_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    parent_id   UUID REFERENCES folders(id) ON DELETE CASCADE,
    deleted     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_folders_owner ON folders(owner_id);
CREATE INDEX idx_folders_parent ON folders(parent_id);

-- Files (metadata only; content lives in GCS)
CREATE TABLE files (
    id              UUID PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    content_type    VARCHAR(150) NOT NULL,
    size_bytes      BIGINT NOT NULL,
    storage_key     VARCHAR(500) NOT NULL,
    owner_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    folder_id       UUID REFERENCES folders(id) ON DELETE SET NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    trashed_at      TIMESTAMP,
    current_version INTEGER NOT NULL DEFAULT 1,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_files_owner ON files(owner_id);
CREATE INDEX idx_files_folder ON files(folder_id);
CREATE INDEX idx_files_status ON files(status);
CREATE INDEX idx_files_name ON files(name);

-- File versions
CREATE TABLE file_versions (
    id              UUID PRIMARY KEY,
    file_id         UUID NOT NULL REFERENCES files(id) ON DELETE CASCADE,
    version_number  INTEGER NOT NULL,
    storage_key     VARCHAR(500) NOT NULL,
    size_bytes      BIGINT NOT NULL,
    content_type    VARCHAR(150) NOT NULL,
    created_by      UUID NOT NULL REFERENCES users(id),
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_file_version_number UNIQUE (file_id, version_number)
);

CREATE INDEX idx_versions_file ON file_versions(file_id);

-- File shares
CREATE TABLE file_shares (
    id              UUID PRIMARY KEY,
    file_id         UUID NOT NULL REFERENCES files(id) ON DELETE CASCADE,
    shared_with_id  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    shared_by_id    UUID NOT NULL REFERENCES users(id),
    permission      VARCHAR(20) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_file_shared_with UNIQUE (file_id, shared_with_id)
);

CREATE INDEX idx_shares_file ON file_shares(file_id);
CREATE INDEX idx_shares_user ON file_shares(shared_with_id);

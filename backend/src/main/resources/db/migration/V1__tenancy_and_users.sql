-- V1: extensions, tenant root, branches, staff.
-- Tenancy model: shared database, shared schema. Every tenant-owned table has clinic_id.
-- Isolation is enforced twice: Hibernate @TenantId (app layer) and PostgreSQL RLS (V9).
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Tenant root (one row per customer clinic / organization). Not RLS-protected: login resolves it by slug.
CREATE TABLE clinic (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  slug                TEXT NOT NULL UNIQUE,          -- subdomain / login identifier
  name                TEXT NOT NULL,
  timezone            TEXT NOT NULL DEFAULT 'Asia/Manila',
  currency            CHAR(3) NOT NULL DEFAULT 'PHP',
  phone               TEXT,
  email               TEXT,
  address             TEXT,
  -- SaaS subscription state
  plan_code           TEXT NOT NULL DEFAULT 'TRIAL',
  subscription_status TEXT NOT NULL DEFAULT 'TRIALING'
                      CHECK (subscription_status IN ('TRIALING','ACTIVE','PAST_DUE','SUSPENDED','CANCELLED')),
  trial_ends_at       TIMESTAMPTZ,
  active              BOOLEAN NOT NULL DEFAULT true,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  version             BIGINT NOT NULL DEFAULT 0
);

-- Per-clinic counters for human-friendly numbers (chart_no, invoice_no).
CREATE TABLE clinic_counter (
  clinic_id  UUID NOT NULL REFERENCES clinic(id),
  name       TEXT NOT NULL,
  next_value BIGINT NOT NULL DEFAULT 1,
  PRIMARY KEY (clinic_id, name)
);

CREATE TABLE branch (
  id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id  UUID NOT NULL REFERENCES clinic(id),
  name       TEXT NOT NULL,
  address    TEXT,
  phone      TEXT,
  active     BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version    BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE chair (
  id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id  UUID NOT NULL REFERENCES clinic(id),
  branch_id  UUID NOT NULL REFERENCES branch(id),
  name       TEXT NOT NULL,
  active     BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version    BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE app_user (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id     UUID NOT NULL REFERENCES clinic(id),
  email         TEXT NOT NULL,
  password_hash TEXT NOT NULL,
  full_name     TEXT NOT NULL,
  role          TEXT NOT NULL CHECK (role IN ('OWNER','ADMIN','DENTIST','FRONT_DESK')),
  active        BOOLEAN NOT NULL DEFAULT true,
  last_login_at TIMESTAMPTZ,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  version       BIGINT NOT NULL DEFAULT 0,
  UNIQUE (clinic_id, email)
);

-- clinic_id defaults to the current tenant so the JPA @ManyToMany join table needs no extra column mapping.
CREATE TABLE user_branch (
  clinic_id UUID NOT NULL DEFAULT nullif(current_setting('app.clinic_id', true), '')::uuid REFERENCES clinic(id),
  user_id   UUID NOT NULL REFERENCES app_user(id),
  branch_id UUID NOT NULL REFERENCES branch(id),
  PRIMARY KEY (user_id, branch_id)
);

CREATE TABLE dentist (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id      UUID NOT NULL REFERENCES clinic(id),
  user_id        UUID REFERENCES app_user(id),
  full_name      TEXT NOT NULL,
  license_no     TEXT,
  specialty      TEXT,
  calendar_color TEXT,
  active         BOOLEAN NOT NULL DEFAULT true,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  version        BIGINT NOT NULL DEFAULT 0
);

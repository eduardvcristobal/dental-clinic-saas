-- V6: audit trail (who viewed/changed what).
CREATE TABLE audit_log (
  id         BIGSERIAL PRIMARY KEY,
  clinic_id  UUID NOT NULL REFERENCES clinic(id),
  user_id    UUID,
  action     TEXT NOT NULL,
  entity     TEXT NOT NULL,
  entity_id  UUID,
  details    JSONB,
  ip_address TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

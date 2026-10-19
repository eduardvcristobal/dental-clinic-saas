-- V3: appointment types, appointments (overlap-proof), reminders, recalls.
CREATE TABLE appointment_type (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id       UUID NOT NULL REFERENCES clinic(id),
  name            TEXT NOT NULL,
  default_minutes INT NOT NULL DEFAULT 30 CHECK (default_minutes > 0),
  color           TEXT,
  active          BOOLEAN NOT NULL DEFAULT true,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE appointment (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id           UUID NOT NULL REFERENCES clinic(id),
  branch_id           UUID NOT NULL REFERENCES branch(id),
  patient_id          UUID NOT NULL REFERENCES patient(id),
  dentist_id          UUID NOT NULL REFERENCES dentist(id),
  chair_id            UUID REFERENCES chair(id),
  appointment_type_id UUID REFERENCES appointment_type(id),
  starts_at           TIMESTAMPTZ NOT NULL,
  ends_at             TIMESTAMPTZ NOT NULL,
  status              TEXT NOT NULL DEFAULT 'BOOKED'
                      CHECK (status IN ('BOOKED','CONFIRMED','ARRIVED','IN_CHAIR','COMPLETED','CANCELLED','NO_SHOW')),
  reason              TEXT,
  notes               TEXT,
  created_by          UUID REFERENCES app_user(id),
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  version             BIGINT NOT NULL DEFAULT 0,
  CHECK (ends_at > starts_at),
  EXCLUDE USING gist (dentist_id WITH =, tstzrange(starts_at, ends_at) WITH &&)
    WHERE (status NOT IN ('CANCELLED','NO_SHOW')),
  EXCLUDE USING gist (chair_id WITH =, tstzrange(starts_at, ends_at) WITH &&)
    WHERE (chair_id IS NOT NULL AND status NOT IN ('CANCELLED','NO_SHOW'))
);

CREATE TABLE reminder (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id      UUID NOT NULL REFERENCES clinic(id),
  appointment_id UUID NOT NULL REFERENCES appointment(id),
  channel        TEXT NOT NULL CHECK (channel IN ('SMS','EMAIL')),
  scheduled_for  TIMESTAMPTZ NOT NULL,
  sent_at        TIMESTAMPTZ,
  status         TEXT NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED','SENT','FAILED','REPLIED')),
  patient_reply  TEXT,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  version        BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE recall (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id    UUID NOT NULL REFERENCES clinic(id),
  patient_id   UUID NOT NULL REFERENCES patient(id),
  due_date     DATE NOT NULL,
  reason       TEXT DEFAULT 'Routine checkup',
  contacted_at TIMESTAMPTZ,
  completed    BOOLEAN NOT NULL DEFAULT false,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  version      BIGINT NOT NULL DEFAULT 0
);

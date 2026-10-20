-- M8.1–M8.8: clinic-scoped notification policy, opt-in and delivery queue. No external sending enabled by migration.
CREATE TABLE notification_policy(
 clinic_id UUID PRIMARY KEY REFERENCES clinic(id),
 enabled BOOLEAN NOT NULL DEFAULT FALSE,
 email_enabled BOOLEAN NOT NULL DEFAULT FALSE,
 sms_enabled BOOLEAN NOT NULL DEFAULT FALSE,
 reminder_hours_before INTEGER NOT NULL DEFAULT 24 CHECK(reminder_hours_before BETWEEN 1 AND 720),
 quiet_start TIME NOT NULL DEFAULT '20:00', quiet_end TIME NOT NULL DEFAULT '08:00',
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE notification_consent(
 clinic_id UUID NOT NULL REFERENCES clinic(id), patient_id UUID NOT NULL,
 channel TEXT NOT NULL CHECK(channel IN('EMAIL','SMS')),
 consented BOOLEAN NOT NULL DEFAULT FALSE,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 PRIMARY KEY(clinic_id,patient_id,channel),
 FOREIGN KEY(clinic_id,patient_id) REFERENCES patient(clinic_id,id)
);
CREATE TABLE notification_delivery(
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 clinic_id UUID NOT NULL,
 patient_id UUID NOT NULL,
 appointment_id UUID,
 recall_id UUID,
 channel TEXT NOT NULL CHECK(channel IN('EMAIL','SMS')),
 category TEXT NOT NULL CHECK(category IN('APPOINTMENT_REMINDER','RECALL')),
 status TEXT NOT NULL DEFAULT 'PENDING' CHECK(status IN('PENDING','CLAIMED','DELIVERED','FAILED','SUPPRESSED')),
 scheduled_for TIMESTAMPTZ NOT NULL,
 attempts INTEGER NOT NULL DEFAULT 0 CHECK(attempts BETWEEN 0 AND 10),
 next_attempt_at TIMESTAMPTZ,
 lease_until TIMESTAMPTZ,
 provider_reference TEXT,
 failure_reason TEXT,
 sent_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(clinic_id,appointment_id,channel,category),
 UNIQUE(clinic_id,recall_id,channel,category),
 CHECK ((appointment_id IS NOT NULL)::int+(recall_id IS NOT NULL)::int=1),
 FOREIGN KEY(clinic_id,patient_id) REFERENCES patient(clinic_id,id),
 FOREIGN KEY(clinic_id,appointment_id) REFERENCES appointment(clinic_id,id),
 FOREIGN KEY(clinic_id,recall_id) REFERENCES recall(clinic_id,id)
);
CREATE INDEX notification_ready_idx ON notification_delivery(status,scheduled_for,next_attempt_at);
CREATE UNIQUE INDEX notification_delivery_clinic_id ON notification_delivery(clinic_id,id);
CREATE TABLE notification_response(
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),clinic_id UUID NOT NULL,delivery_id UUID NOT NULL,
 response TEXT NOT NULL CHECK(response IN('CONFIRM','CANCEL_REQUEST','RESCHEDULE_REQUEST')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(clinic_id,delivery_id),
 FOREIGN KEY(clinic_id,delivery_id) REFERENCES notification_delivery(clinic_id,id)
);
DO $$ DECLARE t TEXT; BEGIN
 FOREACH t IN ARRAY ARRAY['notification_policy','notification_consent','notification_delivery','notification_response']
 LOOP
 EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',t);
 EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',t);
 EXECUTE format('CREATE POLICY tenant_isolation ON %I USING (clinic_id=nullif(current_setting(''app.clinic_id'',true),'''')::uuid) WITH CHECK(clinic_id=nullif(current_setting(''app.clinic_id'',true),'''')::uuid)',t);
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='dental_app') THEN EXECUTE format('GRANT SELECT,INSERT,UPDATE ON %I TO dental_app',t); END IF;
 END LOOP; END $$;

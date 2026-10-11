-- race_registrations.status becomes the RaceRegistrationStatus enum (PENDING, APPROVED, REJECTED).
-- (Cot status cua race_registrations chuyen thanh enum RaceRegistrationStatus.)
-- Older rows may carry other free-text values: a race that was scheduled or already run was an approved one,
-- a cancelled one counts as rejected, and anything else goes back to the manager as pending.
UPDATE race_registrations
SET status = CASE
    WHEN upper(status) IN ('PENDING', 'APPROVED', 'REJECTED') THEN upper(status)
    WHEN upper(status) IN ('COMPLETED', 'REGISTERED', 'SCHEDULED', 'CONFIRMED', 'ACTIVE') THEN 'APPROVED'
    WHEN upper(status) IN ('CANCELLED', 'CANCELED', 'WITHDRAWN') THEN 'REJECTED'
    ELSE 'PENDING'
END
WHERE status IS NULL OR status NOT IN ('PENDING', 'APPROVED', 'REJECTED');

ALTER TABLE race_registrations ALTER COLUMN status SET DEFAULT 'PENDING';
ALTER TABLE race_registrations ALTER COLUMN status SET NOT NULL;
ALTER TABLE race_registrations
    ADD CONSTRAINT chk_race_registrations_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'));

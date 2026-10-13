-- Add two veterinarians, two head trainers, and two grooms for testing.
-- All account and profile data is in English. All six passwords are admin123,
-- encoded with the application's BCrypt password encoder (cost 10).
-- Emails are login identifiers; users has no username column.
-- Duplicate emails intentionally fail the migration instead of changing existing accounts.
-- Backfill missing role-specific profiles for all existing staff accounts as well.
-- Existing profiles, passwords, and assignments are preserved.

INSERT INTO users (full_name, email, password_hash, role_id, phone, address, is_active)
SELECT
    staff.full_name,
    staff.email,
    '$2a$10$1weRcbnyUXb6/Gth/W0yGegtpwVyTju1ZCVL0qZYpEWirPNlrKLiS',
    (SELECT id FROM roles WHERE name = staff.role_name),
    staff.phone,
    staff.address,
    true
FROM (VALUES
    ('James Carter', 'vet1@rtms.com', 'VETERINARIAN',
     '0908000201', '12 Oak Street, Riverside'),
    ('Emily Wilson', 'vet2@rtms.com', 'VETERINARIAN',
     '0908000202', '24 Maple Avenue, Riverside'),
    ('Michael Turner', 'trainer1@rtms.com', 'HEAD_TRAINER',
     '0908000203', '36 Cedar Lane, Greenfield'),
    ('Sarah Bennett', 'trainer2@rtms.com', 'HEAD_TRAINER',
     '0908000204', '48 Willow Road, Greenfield'),
    ('Daniel Brooks', 'groom1@rtms.com', 'GROOM',
     '0908000205', '60 Pine Street, Meadowbrook'),
    ('Olivia Parker', 'groom2@rtms.com', 'GROOM',
     '0908000206', '72 Birch Avenue, Meadowbrook')
) AS staff(full_name, email, role_name, phone, address);

-- Fictional license numbers and dates for new and existing veterinarians.
-- Existing staff without a profile receive deterministic test credentials.
INSERT INTO veterinarian_profiles
    (user_id, license_number, license_issued_date, specialization)
SELECT
    u.id,
    COALESCE(profile.license_number, 'TEST-VET-LEGACY-' || u.id::text),
    COALESCE(profile.license_issued_date, DATE '2020-01-15'),
    COALESCE(profile.specialization, 'General equine medicine and preventive care')
FROM users u
JOIN roles r ON r.id = u.role_id AND r.name = 'VETERINARIAN'
LEFT JOIN (VALUES
    ('vet1@rtms.com', 'TEST-VET-2026-201', DATE '2020-06-15',
     'Equine musculoskeletal care and injury rehabilitation'),
    ('vet2@rtms.com', 'TEST-VET-2026-202', DATE '2022-08-20',
     'Equine internal medicine, nutrition, and preventive care')
) AS profile(email, license_number, license_issued_date, specialization)
    ON profile.email = u.email
ON CONFLICT (user_id) DO NOTHING;

-- Trainer profiles contain certification details; years_of_experience was removed in V54.
INSERT INTO trainer_profiles
    (user_id, certification_number, certification_issued_date)
SELECT
    u.id,
    COALESCE(profile.certification_number, 'TEST-TRN-LEGACY-' || u.id::text),
    COALESCE(profile.certification_issued_date, DATE '2019-01-15')
FROM users u
JOIN roles r ON r.id = u.role_id AND r.name = 'HEAD_TRAINER'
LEFT JOIN (VALUES
    ('trainer1@rtms.com', 'TEST-TRN-2026-201', DATE '2019-04-10'),
    ('trainer2@rtms.com', 'TEST-TRN-2026-202', DATE '2021-09-05')
) AS profile(email, certification_number, certification_issued_date)
    ON profile.email = u.email
ON CONFLICT (user_id) DO NOTHING;

-- Backfill profiles for all grooms, including the existing groom@rtms.com account.
-- Club Manager can assign areas to the new trainers and stall blocks to the grooms.
INSERT INTO groom_profiles (user_id, trainer_id)
SELECT u.id, NULL
FROM users u
JOIN roles r ON r.id = u.role_id AND r.name = 'GROOM'
ON CONFLICT (user_id) DO NOTHING;

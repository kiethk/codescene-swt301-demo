-- =====================================================================
-- V74: Replace the subject_categories table with an enum column on subjects
-- =====================================================================
--
-- subject_categories had 4 seeded rows, no create/update/delete API and no business rule attached: it was a
-- label shown next to a subject, not an entity. It becomes subjects.category, an enum stored as text, in the
-- same way surface_type and intensity_level already are.

-- 1. New column, filled from the old table by category NAME. The names below are the 4 rows seeded by V23.
ALTER TABLE subjects ADD COLUMN category VARCHAR(30);

UPDATE subjects s
SET category = CASE c.name
        WHEN 'Thể lực & Sức bền'         THEN 'ENDURANCE'
        WHEN 'Tốc độ & Nước rút'         THEN 'SPEED'
        WHEN 'Kỹ thuật lồng xuất phát'   THEN 'GATE_START'
        WHEN 'Hồi phục & Thả lỏng'       THEN 'RECOVERY'
    END
FROM subject_categories c
WHERE s.category_id = c.id;

-- 2. A subject whose category name is not one of the 4 seeded names is left NULL by the UPDATE above, so
--    this NOT NULL fails loudly and stops the migration instead of silently defaulting the category.
ALTER TABLE subjects ALTER COLUMN category SET NOT NULL;

ALTER TABLE subjects
    ADD CONSTRAINT chk_subjects_category
        CHECK (category IN ('ENDURANCE', 'SPEED', 'GATE_START', 'RECOVERY'));

-- 3. Drop the old link (the foreign key goes with the column) and the table.
ALTER TABLE subjects DROP COLUMN category_id;
DROP TABLE subject_categories;

-- 4. The permission of the removed GET /api/subject-categories endpoint.
DELETE FROM role_permissions
WHERE permission_id IN (SELECT id FROM permissions WHERE code = 'SUBJECT_CATEGORY_VIEW');

DELETE FROM permissions WHERE code = 'SUBJECT_CATEGORY_VIEW';

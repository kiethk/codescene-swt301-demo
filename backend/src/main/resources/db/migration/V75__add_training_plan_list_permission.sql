-- =====================================================================
-- V75: Separate permission for listing all training plans
-- =====================================================================
--
-- TRAINING_PLAN_VIEW was shared by 4 endpoints and granted to trainer, manager, owner and vet, so an owner or a
-- vet could list every plan. Listing now needs TRAINING_PLAN_LIST (trainer and manager only). TRAINING_PLAN_VIEW
-- stays for opening one plan by id, where the service restricts an owner to plans of their own horses.

INSERT INTO permissions (code, description)
SELECT 'TRAINING_PLAN_LIST', 'List all training plans (trainer and club manager only)'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'TRAINING_PLAN_LIST');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'TRAINING_PLAN_LIST'
WHERE r.name IN ('HEAD_TRAINER', 'CLUB_MANAGER')
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

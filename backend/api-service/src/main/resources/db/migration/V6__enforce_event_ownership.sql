INSERT INTO users (
    id,
    email,
    password_hash,
    status
)
SELECT
    '00000000-0000-0000-0000-000000000001',
    'legacy-organizer@slotforge.invalid',
    '{noop}LOGIN_DISABLED',
    'DISABLED'
WHERE EXISTS (
    SELECT 1
    FROM events
    WHERE organizer_id IS NULL
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO user_roles (
    user_id,
    role_id
)
SELECT
    '00000000-0000-0000-0000-000000000001',
    roles.id
FROM roles
WHERE roles.name = 'ORGANIZER'
  AND EXISTS (
      SELECT 1
      FROM users
      WHERE id = '00000000-0000-0000-0000-000000000001'
  )
ON CONFLICT (user_id, role_id) DO NOTHING;

UPDATE events
SET organizer_id =
        '00000000-0000-0000-0000-000000000001'
WHERE organizer_id IS NULL;

ALTER TABLE events
    ALTER COLUMN organizer_id SET NOT NULL;

-- Default Seed Users for Local Testing & Development
-- Passwords:
-- customer@ecomm.com -> Customer123!
-- admin@ecomm.com    -> Admin123!
-- seller@ecomm.com   -> Seller123!

INSERT INTO users (id, uuid, email, password_hash, first_name, last_name, phone_number, status, email_verified, created_at, updated_at)
VALUES
    (1, 'c0000000-0000-0000-0000-000000000001', 'customer@ecomm.com', '$2a$12$boTjsp6yEiD2kx1RQcZLl.vsc/dxGN4CHHNK/6DM5bopPEAJOg.Z2', 'Alice', 'Customer', '+1000000001', 'ACTIVE', TRUE, NOW(), NOW()),
    (2, 'a0000000-0000-0000-0000-000000000002', 'admin@ecomm.com',    '$2a$12$SKqFeyVBVJmJ/SsgtQl/CegGEdo4frLp6HEtU51zFsw8.F7H3z7re', 'Admin', 'Root',     '+1000000002', 'ACTIVE', TRUE, NOW(), NOW()),
    (3, 's0000000-0000-0000-0000-000000000003', 'seller@ecomm.com',   '$2a$12$boTjsp6yEiD2kx1RQcZLl.vsc/dxGN4CHHNK/6DM5bopPEAJOg.Z2', 'Bob',   'Merchant', '+1000000003', 'ACTIVE', TRUE, NOW(), NOW())
ON DUPLICATE KEY UPDATE email=email;

-- Assign Roles
-- Customer -> ROLE_CUSTOMER
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.email = 'customer@ecomm.com' AND r.name = 'ROLE_CUSTOMER'
ON DUPLICATE KEY UPDATE user_id=user_id;

-- Admin -> ROLE_ADMIN and ROLE_CUSTOMER
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.email = 'admin@ecomm.com' AND r.name IN ('ROLE_ADMIN', 'ROLE_CUSTOMER')
ON DUPLICATE KEY UPDATE user_id=user_id;

-- Seller -> ROLE_SELLER
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.email = 'seller@ecomm.com' AND r.name = 'ROLE_SELLER'
ON DUPLICATE KEY UPDATE user_id=user_id;


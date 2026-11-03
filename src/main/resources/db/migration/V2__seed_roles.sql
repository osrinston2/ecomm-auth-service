INSERT INTO roles (name, description) VALUES
    ('ROLE_CUSTOMER', 'Standard e-commerce customer/buyer with access to shopping, cart, orders, and profile'),
    ('ROLE_SELLER', 'Merchant/Vendor with access to inventory, product management, and vendor order fulfillment'),
    ('ROLE_ADMIN', 'Platform Administrator with full access to service configuration and user management'),
    ('ROLE_SUPPORT', 'Customer care support staff with permissions to assist users and manage dispute tickets')
ON DUPLICATE KEY UPDATE description = VALUES(description);


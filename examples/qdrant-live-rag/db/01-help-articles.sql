-- Source of truth for the demo: a small set of fictional "Northwind" help-center docs.
-- Synthetic data only. One fact (e.g. the free-trial length) lives in exactly one row,
-- so an UPDATE produces a clean, unambiguous answer flip on camera.

CREATE TABLE help_articles (
  id          serial PRIMARY KEY,
  title       text        NOT NULL,
  category    text        NOT NULL,
  body        text        NOT NULL,
  updated_at  timestamptz NOT NULL DEFAULT now()
);

-- Emit full row images on UPDATE so CDC carries every column.
ALTER TABLE help_articles REPLICA IDENTITY FULL;

INSERT INTO help_articles (title, category, body) VALUES
('Plans & Pricing', 'Billing',
 'Northwind offers three plans: Free (1 project, 3 members), Pro at $12 per user/month, and Business at $29 per user/month with advanced admin controls.'),
('Free Trial', 'Billing',
 'Every paid plan includes a 14-day free trial. No credit card is required to start; your trial converts to a paid subscription only after you add billing details.'),
('Refunds', 'Billing',
 'Annual plans can be refunded by contacting support within your first billing cycle. Monthly plans are non-refundable but can be cancelled anytime.'),
('Cancelling Your Subscription', 'Billing',
 'You can cancel under Settings -> Billing. Your plan stays active until the end of the current billing period, after which your account reverts to Free.'),
('Exporting Your Data', 'Data',
 'You can export all projects and tasks to CSV or JSON at any time from Settings -> Data -> Export. Exports are emailed as a download link.'),
('Data Security', 'Security',
 'All data is encrypted at rest with AES-256 and in transit with TLS 1.2 or higher. Northwind is SOC 2 Type II certified.'),
('Two-Factor Authentication', 'Security',
 'Two-factor authentication is available via any TOTP authenticator app. Enable it under Settings -> Security -> 2FA.'),
('Supported Integrations', 'Integrations',
 'Northwind integrates natively with Slack, GitHub, and Google Calendar. Additional integrations are available via webhooks.'),
('Support Hours', 'Support',
 'Email support is available Monday to Friday, 9am-6pm ET. Business plan customers receive priority response within 4 business hours.'),
('Mobile App', 'Product',
 'Northwind is available on iOS and Android. Your projects sync automatically across web and mobile.');

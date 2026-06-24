-- INSERT beat: a topic that didn't exist. Ask "Do you support single sign-on / Okta?"
-- before (bot: "I don't have that information") and seconds after (bot answers from this new doc).
INSERT INTO help_articles (title, category, body) VALUES
('Single Sign-On (SSO)', 'Security',
 'Single sign-on via SAML 2.0 with Okta, Google Workspace, and Microsoft Entra ID is available on the Business plan. Admins can enforce SSO for all members under Settings -> Security -> SSO.');

-- UPDATE beat: a clean number flip. Ask "How long is the free trial?" before and after.
-- Trial length lives in exactly one row, so the answer flips 14 -> 30 with nothing else to confuse retrieval.
UPDATE help_articles
SET body = 'Every paid plan includes a 30-day free trial. No credit card is required to start; your trial converts to a paid subscription only after you add billing details.',
    updated_at = now()
WHERE title = 'Free Trial';

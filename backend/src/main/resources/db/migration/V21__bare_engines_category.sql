-- Engines split in two: complete engines (with their ancillaries) and bare engines (long blocks, short blocks and bare
-- engines). The title decides; eBay's sub type only where the title doesn't say. New listings are sorted the same way
-- on import (CategoryMapper.refine). updated_at is left alone: the eBay sync compares it with ebay_synced_at.
INSERT INTO categories (id, name, popular, sort_order, image_key)
VALUES ('bare-engines', 'Bare Engines', false, 2, 'categories/engines-photo-v1.jpg')
ON CONFLICT (id) DO NOTHING;

UPDATE categories SET sort_order = sort_order + 1 WHERE id NOT IN ('engines', 'bare-engines') AND sort_order >= 2;

UPDATE categories SET name = 'Complete Engines' WHERE id = 'engines' AND name = 'Engines';

UPDATE products SET category_id = 'bare-engines'
WHERE category_id = 'engines'
  AND (name ~* 'bare|long block|short block|short motor|engine block|block only'
       OR (name !~* 'complete|full engine' AND sub_type IN ('Engine Long Block', 'Engine Short Block', 'Engine Block')));

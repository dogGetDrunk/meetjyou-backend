-- Remove duplicate rows before adding unique constraint
DELETE p1 FROM preference p1
    INNER JOIN preference p2 ON p1.type = p2.type AND p1.name = p2.name
WHERE p1.id > p2.id;

ALTER TABLE preference ADD UNIQUE KEY uk_preference_type_name (type, name);

-- Seed all preference values; INSERT IGNORE is safe for production DBs that already have this data
INSERT IGNORE INTO preference (type, name)
VALUES ('GENDER', 'M'),
       ('GENDER', 'F'),
       ('GENDER', 'O'),
       ('AGE', 'TEEN'),
       ('AGE', 'TWENTY'),
       ('AGE', 'THIRTY'),
       ('AGE', 'FORTY'),
       ('AGE', 'OLDER'),
       ('PERSONALITY', 'INTROVERTED'),
       ('PERSONALITY', 'EXTROVERTED'),
       ('PERSONALITY', 'SOCIAL'),
       ('PERSONALITY', 'OPTIMISTIC'),
       ('PERSONALITY', 'FREE'),
       ('PERSONALITY', 'PRACTICAL'),
       ('PERSONALITY', 'CAREFUL'),
       ('PERSONALITY', 'BOLD'),
       ('TRAVEL_STYLE', 'ACTIVITY'),
       ('TRAVEL_STYLE', 'RELAX'),
       ('TRAVEL_STYLE', 'CULTURE'),
       ('TRAVEL_STYLE', 'FOOD'),
       ('TRAVEL_STYLE', 'SPORTS'),
       ('TRAVEL_STYLE', 'BUDGET'),
       ('TRAVEL_STYLE', 'LUXURY'),
       ('TRAVEL_STYLE', 'ADVENTURE'),
       ('TRAVEL_STYLE', 'NATURE'),
       ('TRAVEL_STYLE', 'URBAN'),
       ('TRAVEL_STYLE', 'ART'),
       ('TRAVEL_STYLE', 'SHOP'),
       ('DIET', 'VEGETARIAN'),
       ('DIET', 'GLUTEN_FREE'),
       ('DIET', 'VEGAN'),
       ('DIET', 'SPECIFIC'),
       ('DIET', 'ANYTHING'),
       ('ETC', 'SMOKE'),
       ('ETC', 'NOT_SMOKE'),
       ('ETC', 'DRINK'),
       ('ETC', 'NOT_DRINK'),
       ('ETC', 'DONT_CARE');

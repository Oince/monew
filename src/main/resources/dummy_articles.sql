

DO $$
BEGIN
    FOR i IN 1..100000 LOOP
        INSERT INTO articles (id, source, source_url, title, summary, publish_date)
        VALUES (
            gen_random_uuid(),
            'Dummy Source ' || i,
            'http://dummy-url.com/' || i,
            'Dummy Title ' || i,
            'Dummy Summary ' || i,
            now() - (i * interval '1 minute')
        );
    END LOOP;
END $$;
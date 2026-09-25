ALTER TABLE krav
    ALTER COLUMN vedtaksdato TYPE DATE USING vedtaksdato::date,
    ALTER COLUMN utbetaldato TYPE DATE USING utbetaldato::date,
    ALTER COLUMN tidspunkt_sendt TYPE TIMESTAMPTZ USING tidspunkt_opprettet AT TIME ZONE 'Europe/Oslo',
    ALTER COLUMN tidspunkt_siste_status TYPE TIMESTAMPTZ USING tidspunkt_opprettet AT TIME ZONE 'Europe/Oslo',
    ALTER COLUMN tidspunkt_opprettet TYPE TIMESTAMPTZ USING tidspunkt_opprettet AT TIME ZONE 'Europe/Oslo';

ALTER TABLE feilmelding
    ALTER COLUMN tidspunkt_opprettet TYPE TIMESTAMPTZ USING tidspunkt_opprettet AT TIME ZONE 'Europe/Oslo';

ALTER TABLE filvalideringsfeil
    ALTER COLUMN tidspunkt_opprettet TYPE TIMESTAMPTZ USING tidspunkt_opprettet AT TIME ZONE 'Europe/Oslo';
create index if not exists idx_krav_opprettet on krav(tidspunkt_opprettet);

create index if not exists idx_valideringsfeil_opprettet on filvalideringsfeil(tidspunkt_opprettet);

create index if not exists idx_feilmelding_opprettet on feilmelding(tidspunkt_opprettet);

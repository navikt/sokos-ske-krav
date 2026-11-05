create index if not exists idx_feilmelding_saksnummer_nav on feilmelding(saksnummer_nav);
create index if not exists idx_krav_saksnummer_nav_med_ske_ident on krav(saksnummer_nav) where nullif(kravidentifikator_ske, '') is not null;
create index if not exists idx_krav_gammelsak_med_ske_ident on krav(referansenummergammelsak) where nullif(kravidentifikator_ske, '') is not null;

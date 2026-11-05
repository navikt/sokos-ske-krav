insert into feilmelding (krav_id, corr_id, saksnummer_nav, kravidentifikator_ske, error, melding, nav_request, ske_response)
values
    (0, 'CORR-OPPRYDDING-ENDRING-OB04', 'OB040000595067', null, '422', 'foreldelsesfristensUtgangspunkt kan ikke være mer enn 2 år og 10 måneder tilbake i tid.', '{}', '{}');

insert into krav (
    filnavn, linjenummer, saksnummer_nav, kravidentifikator_ske, belop, vedtaksdato,
    gjelder_id, periode_fom, periode_tom, kravkode, referansenummergammelsak,
    transaksjonsdato, enhet_bosted, enhet_behandlende, kode_hjemmel, kode_arsak,
    belop_rente, fremtidig_ytelse, fagsystem_id, status, kravtype, corr_id, avsender
) values (
    'lokalt-avvist-opprydding.txt', 1, 'OB040000595068', null, 103.77, current_date,
    '12345678901', '20221101', '20230430', 'EF OG', 'OB040000595067',
    '20230526', '4803', '4819', 'T', '', 0.00, 0.00, '8020', 'VALIDERINGSFEIL_AV_LINJE_I_FIL', 'NYTT_KRAV', 'lokalt-avvist-opprydding-corr-id', 'OB04'
);

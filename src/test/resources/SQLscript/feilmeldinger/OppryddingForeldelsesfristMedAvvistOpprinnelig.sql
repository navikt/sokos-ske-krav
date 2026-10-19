insert into feilmelding (krav_id, corr_id, saksnummer_nav, kravidentifikator_ske, error, melding, nav_request, ske_response)
values
    (0, 'CORR-OPPRYDDING-ENDRING', '2222-navsaksnr', null, '422', 'foreldelsesfristensUtgangspunkt kan ikke være mer enn 2 år og 10 måneder tilbake i tid.', '{}', '{}');

insert into krav (
    filnavn, linjenummer, saksnummer_nav, kravidentifikator_ske, belop, vedtaksdato,
    gjelder_id, periode_fom, periode_tom, kravkode, referansenummergammelsak,
    transaksjonsdato, enhet_bosted, enhet_behandlende, kode_hjemmel, kode_arsak,
    belop_rente, fremtidig_ytelse, fagsystem_id, status, kravtype, corr_id
) values (
    'opprinnelig-avvist.txt', 1, '2222-navsaksnr', null, 100.00, current_date,
    '12345678901', '20230101', '20230131', 'PE AP', '',
    '20230526', '4803', '4819', 'T', '', 0.00, 0.00, 'fagsystem', '422_VALIDERINGSFEIL', 'NYTT_KRAV', 'opprinnelig-avvist-corr-id'
);

insert into feilmelding (krav_id, corr_id, saksnummer_nav, kravidentifikator_ske, error, melding, nav_request, ske_response)
values
    (0, 'CORR-OPPRYDDING-ENDRING', '2222-navsaksnr', null, '422', 'foreldelsesfristensUtgangspunkt kan ikke være mer enn 2 år og 10 måneder tilbake i tid.', '{}', '{}'),
    (0, 'CORR-OPPRYDDING-ENDRING-OB04', 'OB040000595067', null, '422', 'foreldelsesfristensUtgangspunkt kan ikke være mer enn 2 år og 10 måneder tilbake i tid.', '{}', '{}'),
    (0, 'CORR-OPPRYDDING-STOPP', 'OB040000520015', null, '422', 'SKE svarte: foreldelsesfristensUtgangspunkt kan ikke være mer enn 2 år og 10 måneder tilbake i tid.', '{}', '{}'),
    (0, 'CORR-ANNEN-FEIL', '8888-navsaksnr', null, '422', 'foreldelsesfristensUtgangspunkt har ugyldig format.', '{}', '{}');

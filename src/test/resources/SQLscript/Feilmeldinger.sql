insert into feilmelding (krav_id, corr_id, saksnummer_nav, kravidentifikator_ske, error, melding, nav_request, ske_response, tidspunkt_opprettet, rapporter)
values (1, 'CORR856', '1110-navsaksnummer', '1111-skeUUID', 422, 'feilmelding 422 1111', '{nav request 1}', '{ske response 1}', now(), true ),
       (2, 'CORR658', '2220-navsaksnummer', '2222-skeUUID', 404, 'feilmelding 404 2222', '{nav request 2}', '{ske response 2}', now(), true),
       (2, 'CORR658', '2220-navsaksnummer', '2222-skeUUID', 404, 'feilmelding 422 2222', '{nav request 2}', '{ske response 2}', now(), true),
       (3, 'CORR457389', '1116-navsaksnummer', '1919-skeUUID', 500, 'feilmelding 500 1919', '{nav request 2}', '{ske response 2}', now(), true );
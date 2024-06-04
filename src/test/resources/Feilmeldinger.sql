insert into feilmelding (kravID, corr_id, saksnummer, kravidentifikator_ske, error, melding, navRequest, skeResponse, tidspunkt_opprettet)
values (1, 'CORR856', '1110-navsaksnummer', '1111-skeUUID', 422, 'feilmelding 422 1111', '{nav request 1}', '{ske response 1}', now() ),
       (2, 'CORR658', '2220-navsaksnummer', '2222-skeUUID', 404, 'feilmelding 404 2222', '{nav request 2}', '{ske response 2}', now() ),
       (2, 'CORR658', '2220-navsaksnummer', '2222-skeUUID', 422, 'feilmelding 422 2222', '{nav request 2}', '{ske response 2}', now() );
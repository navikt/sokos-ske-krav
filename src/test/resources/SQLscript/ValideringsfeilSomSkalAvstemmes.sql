insert into valideringsfeil (id, filnavn, linjenummer, saksnummer_nav, kravlinje, feilmelding, tidspunkt_opprettet, rapporter)
values (1, 'ValideringsfeilSomSkalAvstemmes.txt', 11, '1111-navsaksnummer', 'kravlinje 1', 'feilmelding 422 1111',  now(), true),
       (2, 'ValideringsfeilSomSkalAvstemmes.txt', 22, '2222-navsaksnummer', 'kravlinje 2', 'feilmelding 404 2222', now() , true),
       (3, 'ValideringsfeilSomSkalAvstemmes.txt', 33, '3333-navsaksnummer', 'kravlinje 3', 'feilmelding 404 3333', now(), true);
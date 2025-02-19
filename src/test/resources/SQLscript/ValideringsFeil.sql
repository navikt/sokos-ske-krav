insert into valideringsfeil (id, filnavn, linjenummer, saksnummer_nav, kravlinje, feilmelding, tidspunkt_opprettet)
values
    (11, 'Fil1.txt', 1, '111', 'linje1', 'feilmelding1', NOW()),
    (21, 'Fil2.txt', 2, '222', 'linje2.1', 'feilmelding2.1', NOW()),
    (22, 'Fil2.txt', 2, '222', 'linje2.2', 'feilmelding2.2', NOW()),
    (31, 'Fil3.txt', 3, '333', 'linje3.1', 'feilmelding3.1', NOW()),
    (32, 'Fil3.txt', 3, '333', 'linje3.2', 'feilmelding3.2', NOW()),
    (33, 'Fil3.txt', 3, '333', 'linje3.3', 'feilmelding3.3', NOW());

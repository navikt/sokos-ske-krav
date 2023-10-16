INSERT INTO krav(saksnummer_nav, saksnummer_ske, fildata_nav, jsondata_ske, status, dato_sendt, dato_siste_status, kravtype)
VALUES('1111-navuuid', '1111-ske', 'fildata fra nav 1', 'json fra ske 1', 'status 1', '2023-01-01', '2023-01-02', 'NYTT_KRAV');

INSERT INTO krav(saksnummer_nav, saksnummer_ske, fildata_nav, jsondata_ske, status, dato_sendt, dato_siste_status, kravtype)
VALUES('2222-navuuid', '2222-ske', 'fildata fra nav 2', 'json fra ske 2', 'status 2', '2023-02-01', '2023-02-02', 'NYTT_KRAV');


INSERT INTO kobling (saksref_fil, saksref_uuid, dato)
VALUES ('1110-navfil', '1111-navuuid', '2023-01-01');

INSERT INTO kobling (saksref_fil, saksref_uuid, dato)
VALUES ('2220-navfil', '2222-navuuid', '2023-02-01');
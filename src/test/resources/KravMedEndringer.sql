INSERT INTO krav (kravidentifikator_ske, saksnummer_nav, belop, vedtakDato, gjelderId, periodeFOM, periodeTOM, kravkode, referanseNummerGammelSak, transaksjonDato, enhetBosted, enhetBehandlende, kodeHjemmel, kodeArsak, belopRente, fremtidigYtelse, utbetalDato, fagsystemId, status, kravtype, dato_sendt, dato_siste_status)
VALUES  ( '1234','OB040000573209', '123.00', '2023-05-24 00:00', '12345678901', '20220901', '20221031', 'PE AP','4321', '20230524', '4803', '4819','T','', '0.0', '0.0', '', '', 'MOTTATT_UNDER_BEHANDLING', 'ENDRE_RENTER', '2023-01-01 12:00:00', '2023-01-01 13:00:00'),
        ( '1234','OB040000598553', '456.00', '2023-05-25 00:00', '12345678902', '20221101', '20221231', 'PE AP','4321', '20230525', '4803', '4819','T','', '0.0', '0.0', '', '', 'MOTTATT_UNDER_BEHANDLING', 'ENDRE_HOVEDSTOL', '2023-02-01 12:00:00', '2023-02-01 13:00:00');


INSERT INTO kobling (saksref_fil, saksref_uuid, dato)
VALUES  ('OB040000573209', '1111-navuuid', '2023-01-01'),
        ('OB040000598553', '2222-navuuid', '2023-02-01');

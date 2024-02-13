INSERT INTO krav (kravidentifikator_ske, saksnummer_nav, belop, vedtakDato, gjelderId, periodeFOM, periodeTOM, kravkode, referanseNummerGammelSak, transaksjonDato, enhetBosted, enhetBehandlende, kodeHjemmel, kodeArsak, belopRente, fremtidigYtelse, utbetalDato, fagsystemId, status, kravtype, corr_id, dato_sendt, dato_siste_status)
VALUES  ( '1111-skeUUID','1110-navsaksnummer', '123.00', '2023-05-24 00:00', '12345678901', '20220901', '20221031', 'PE AP','', '20230524', '4803', '4819','T','', '0.0', '0.0', '', '', 'RESKONTROFOERT', 'NYTT_KRAV', 'CORR%^&', '2023-01-01 12:00:00', '2023-01-01 13:00:00'),
         ( '2222-skeUUID','2220-navsaksnummer', '456.00', '2023-05-25 00:00', '12345678902', '20221101', '20221231', 'PE AP','', '20230525', '4803', '4819','T','', '0.0', '0.0', '', '', 'RESKONTROFOERT', 'NYTT_KRAV', 'CORR%^&', '2023-02-01 12:00:00', '2023-02-01 13:00:00');


INSERT INTO kobling (saksref_fil, saksref_uuid, dato)
VALUES  ('1110-navsaksnummer', '1111-navuuid', '2023-01-01'),
        ('2220-navsaksnummer', '2222-navuuid', '2023-02-01');

package sokos.ske.krav.service

import mu.KotlinLogging

class AvstemmingService(
  private val databaseService: DatabaseService = DatabaseService(),
) {
  private val logger = KotlinLogging.logger {}

  private suspend fun avstemmKrav() {
	  val krav = databaseService.hentKravSomSkalAvstemmes()
	  val totaltAntall = krav.size;



  }

}

package sokos.ske.krav.util

import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.ext.ScriptUtils
import org.testcontainers.ext.ScriptUtils.ScriptLoadException
import org.testcontainers.jdbc.JdbcDatabaseDelegate
import org.testcontainers.utility.DockerImageName
import java.io.File

class TestContainer(private val name: String = "testContainer") {
	private val dockerImageName = "postgres:latest"
	private val container = PostgreSQLContainer<Nothing>(DockerImageName.parse(dockerImageName))

	fun getContainer(
		scripts: List<String> = emptyList(),
		reusable: Boolean = false,
		loadFlyway: Boolean = false
	): PostgreSQLContainer<Nothing> {

		//Må starte container før runInitScript
		container.apply {
			withCreateContainerCmdModifier { cmd -> cmd.withName(name) }
			withReuse(reusable)
			start()
		}

		if (loadFlyway) {
			copyFlywayScripts().forEach { script ->
				try {
					ScriptUtils.runInitScript(JdbcDatabaseDelegate(container, ""), script)
				} catch (e: ScriptLoadException) {
					println("Vent på at filene er kopiert og kjør testen på nytt. EXCEPTION: ${e.message}")
				}
			}
		}
		if (scripts.isNotEmpty()) {
			scripts.forEach { script ->
				ScriptUtils.runInitScript(JdbcDatabaseDelegate(container, ""), script)
			}
		}
		return container

	}


	fun stopAnyRunningContainer() {
		val query = """echo $(docker rm $(docker kill $(docker ps -a -q --filter="ancestor=${dockerImageName}")))"""
		val p = ProcessBuilder("/bin/bash", "-c", query).start()
		p.waitFor()
		val lines = p.inputReader(Charsets.UTF_8).readLines()
		val id = lines.ifEmpty { listOf() }.first()
		println("stopped running container with id $id")
	}


	private fun copyFlywayScripts(path: String = "src/main/resources/db/migration"): List<String> {
		val files =
			getFlyWayScripts(path).map {
			  val new = File("src/test/resources/${it.name}")
				new.writeText("")
				val file = it.copyTo(new, true)
				file
			}
		return files.map { it.name }
	}

	private fun getFlyWayScripts(path: String = "src/main/resources/db/migration"): List<File> =
		File(path).walk().filter { it.name.endsWith(".sql") }.toList().sortedBy { it.name }
}
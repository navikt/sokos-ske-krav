package sokos.ske.krav.util


import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.ext.ScriptUtils
import org.testcontainers.ext.ScriptUtils.ScriptLoadException
import org.testcontainers.jdbc.JdbcDatabaseDelegate
import org.testcontainers.utility.DockerImageName
import sokos.ske.krav.config.PropertiesConfig
import sokos.ske.krav.database.PostgresDataSource
import java.io.File

class TestContainer {
    private val container = PostgreSQLContainer(DockerImageName.parse("postgres:latest"))

    fun getDataSource(initScript: String, reusable: Boolean = false, loadFlyway: Boolean = false): PostgresDataSource = PostgresDataSource(initContainer(listOf(initScript), reusable, loadFlyway))

    private fun initContainer(scripts: List<String>, reusable: Boolean = false, loadFlyway: Boolean = false): PropertiesConfig.PostgresConfig {

        //Må starte container før runInitScript
        container.apply {
            withReuse(reusable)
            start()
        }

        if(loadFlyway){
            copyFlywayScripts().forEach { script ->
                try {
                    ScriptUtils.runInitScript(JdbcDatabaseDelegate(container, ""), script)
                } catch(e: ScriptLoadException){
                    println("Vent på at filene er kopiert og kjør testen på nytt. EXCEPTION: ${e.message}")
                }
            }
        }
        scripts.forEach { script ->
            ScriptUtils.runInitScript(JdbcDatabaseDelegate(container, ""), script)
        }

        return PropertiesConfig.PostgresConfig(
            host = container.host,
            port = container.firstMappedPort.toString(),
            name = container.databaseName,
            username = container.username,
            password = container.password,
        )
    }

    private fun copyFlywayScripts(path: String = "src/main/resources/db/migration"): List<String> {
        val files =
            getFlyWayScripts(path).map {
                val new  = File("src/test/resources/${it.name}")
                new.writeText("")
                val file = it.copyTo(new, true)
                file
            }
        return files.map { it.name }
    }

    private fun getFlyWayScripts(path: String = "src/main/resources/db/migration"): List<File> = File(path).walk().filter { it.name.endsWith(".sql") }.toList().sortedBy { it.name }


}
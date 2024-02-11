package sokos.ske.krav.mq

import com.ibm.mq.jms.MQConnectionFactory
import com.ibm.mq.jms.MQQueue
import com.ibm.msg.client.jms.JmsConstants
import com.ibm.msg.client.wmq.WMQConstants
import mu.KotlinLogging
import sokos.ske.krav.config.PropertiesConfig
import javax.jms.Connection
import javax.jms.MessageProducer
import javax.jms.Session

class MqProducer(
    private val config: PropertiesConfig.MqConfig
) {
    private lateinit var session: Session
    private lateinit var messageProducer: MessageProducer
    private var connected: Boolean = false

    private val logger = KotlinLogging.logger { }
    private val secureLogger = KotlinLogging.logger("secureLogger")

    init {
        connect()
    }

    private fun connect() {
        logger.info("Connecting to MQ queue ${config.oppdragQueue}")
        val connection = config.connect()
        session = connection.createSession(Session.SESSION_TRANSACTED)
        val queue = (session.createQueue(config.oppdragQueue) as MQQueue).apply {
            targetClient = WMQConstants.WMQ_CLIENT_NONJMS_MQ
        }
        messageProducer = session.createProducer(queue)
        connection.start()
        connected = true
    }

    fun send(message: String) {
        try {
            if (!connected) connect()
            messageProducer.send(session.createTextMessage(message))
        } catch (ex: Exception) {
            logger.error("Kunne ikke legge melding på BOQ. Se secure log for fnummer")
            secureLogger.error { "Kunne ikke legge melding: $message på BOQ" }
            connected = false
            throw ex
        }
    }

    fun commit()= session.commit()

    fun rollback() = session.rollback()

    private fun PropertiesConfig.MqConfig.connect(): Connection = MQConnectionFactory().also {
        it.transportType = WMQConstants.WMQ_CM_CLIENT
        it.hostName = host
        it.port = port.toInt()
        it.channel = channel
        it.queueManager = qmgr
        it.targetClientMatching = true
        it.setBooleanProperty(JmsConstants.USER_AUTHENTICATION_MQCSP, true)
    }.createConnection(username, password)
}
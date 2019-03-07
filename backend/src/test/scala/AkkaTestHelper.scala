import java.time.Clock
import java.util.Date

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import play.api.{Configuration, Environment}
import services.{BasicServices, Random, SecretValidator, Services}

object AkkaTestHelper {
  def simpleConfig() = ConfigFactory.parseString(
    """
      |akka.loglevel = "info"
    """.stripMargin)
}

trait AkkaTestHelper extends MockFactory {
  def newClock(): Clock = {
    val clock = mock[Clock]
    val now = new Date()
    (clock.instant _).expects.anyNumberOfTimes.returning(now.toInstant)
    (clock.millis  _).expects.anyNumberOfTimes.returning(now.getTime)
    clock
  }

  def mockServices(system: ActorSystem): Services = {
    val conf = ConfigFactory.parseString(
      """
        |passwords = {
        |  mustBe = {
        |    notOlderThan = 90 #days
        |  }
        |  tokens = {
        |    expiresIn = 7 days
        |    issuer    = "morbid-user"
        |  }
        |}
      """.stripMargin)

    new BasicServices(
      random           = mock[Random],
      environment      = mock[Environment],
      secretValidator  = mock[SecretValidator],
      theClock         = newClock(),
      config           = Configuration(conf),
      executionContext = system.dispatcher,
      system           = system)
  }
}

import java.time.Clock
import java.util.Date

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import play.api.{Configuration, Environment}
import services.{AppServices, AppServicesImpl, Random, SecretValidator}
import xingu.commons.play.services.Services

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

  def mockServices(system: ActorSystem): AppServices = {
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

    new AppServicesImpl(
      ec        = system.dispatcher,
      random    = mock[Random],
      env       = mock[Environment],
      clock     = newClock(),
      config    = Configuration(conf),
      validator = mock[SecretValidator],
      system    = system)
  }
}

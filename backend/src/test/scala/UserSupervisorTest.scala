

import java.util.Date

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import domain.{GetById, UnknownUser, User}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FlatSpecLike, Matchers}
import services.TokenGenerator
import store.{Passwords, Stores, Users, UsersSupervisor}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

class UserSupervisorTest extends TestKit(ActorSystem("UserSupervisorTest", AkkaTestHelper.simpleConfig()))
  with ImplicitSender
  with FlatSpecLike
  with Matchers
  with AkkaTestHelper
  with BeforeAndAfterAll
  with BeforeAndAfterEach {

  override def afterAll {
    TestKit.shutdownActorSystem(system, verifySystemShutdown = true)
  }

  it should "return 'UnknownUser' when user not found" in {

    val tokens    = mock[TokenGenerator]
    val services  = mockServices(system)
    val users     = mock[Users]
    val passwords = mock[Passwords]
    val stores    = mock[Stores]

    (stores.users     _)  .expects()        .returning(users)                     .once()
    (stores.passwords _)  .expects()        .returning(passwords)                 .once()
    (users.byId       _)  .expects(1)   .returning(Future.successful(None))   .once()

    val ref = system.actorOf(UsersSupervisor.props(services, tokens, stores), "users1")

    within(1 seconds) {
      ref ! GetById(1) ; expectMsg(UnknownUser)
    }
  }

  it should "return a 'User' for a known user" in {

    val tokens    = mock[TokenGenerator]
    val services  = mockServices(system)
    val users     = mock[Users]
    val passwords = mock[Passwords]
    val stores    = mock[Stores]
    val user      = User(1, 1, new Date(), None, active = true, "username", "email", "type", None)

    (stores.users     _)  .expects()        .returning(users)                         .once()
    (stores.passwords _)  .expects()        .returning(passwords)                     .once()
    (users.byId       _)  .expects(1)   .returning(Future.successful(Some(user))) .once()

    val ref = system.actorOf(UsersSupervisor.props(services, tokens, stores), "users2")

    within(1 seconds) {
      ref ! GetById(1) ; expectMsg(user)
    }
  }
}

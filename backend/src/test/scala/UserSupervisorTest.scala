

import java.util.Date

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import domain._
import domain.utils._
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

    (stores.users     _)  .expects()      .returning(users)                   .once()
    (stores.passwords _)  .expects()      .returning(passwords)               .once()
    (users.byId       _)  .expects(1) .returning(Future.successful(None)) .once()

    val ref = system.actorOf(UsersSupervisor.props(services, tokens, stores), "users1")

    within(1 seconds) {
      ref ! GetById(1)
      expectMsg(UnknownUser)
    }
  }

  it should "return a 'User' for a known user" in {

    val tokens    = mock[TokenGenerator]
    val services  = mockServices(system)
    val users     = mock[Users]
    val passwords = mock[Passwords]
    val stores    = mock[Stores]
    val user      = User(1, 1, new Date(), None, active = true, "username", "email", "type", None)

    (stores.users     _)  .expects()      .returning(users)                         .once()
    (stores.passwords _)  .expects()      .returning(passwords)                     .once()
    (users.byId       _)  .expects(1) .returning(Future.successful(Some(user))) .once()

    val ref = system.actorOf(UsersSupervisor.props(services, tokens, stores), "users2")

    within(1 seconds) {
      ref ! GetById(1)
      expectMsg(user)
    }
  }

  it should "create a new user" in {
    val tokens     = mock[TokenGenerator]
    val services   = mockServices(system)
    val users      = mock[Users]
    val passwords  = mock[Passwords]
    val stores     = mock[Stores]
    val now        = Date.from(services.clock().instant())
    val user       = User(1, 1, now, None, active = true, "username", "email", "type", None)
    val request    = CreateUserRequest(user.account, user.username, None, user.email, user.`type`)
    val password   = Password(1, 1, now, null, "sha256", "rnd16", "rnd32")
    val pwdRequest = CreatePasswordRequest(user.id, "sha256", "rnd16".sha256(), "rnd32")

    (stores.users     _)  .expects()                .returning(users)                       .once()
    (stores.passwords _)  .expects()                .returning(passwords)                   .once()
    (users.byUsername _)  .expects("username")  .returning(Future.successful(None))     .once()
    (users.create     _)  .expects(request)         .returning(Future.successful(user))     .once()
    (passwords.create _)  .expects(pwdRequest)      .returning(Future.successful(password)) .once()

    services.secrets().generate _ expects 16 returning "rnd16" once()
    services.rnd().generate     _ expects 32 returning "rnd32" once()

    val ref = system.actorOf(UsersSupervisor.props(services, tokens, stores), "users3")
    within(1 minute) {
      ref ! request
      expectMsg(user.copy(password = Some(password.copy(method = "plain"))))
    }
  }
}
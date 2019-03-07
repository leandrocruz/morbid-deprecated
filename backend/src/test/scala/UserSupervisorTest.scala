

import java.util.Date

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import domain._
import domain.utils._
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FlatSpecLike, Matchers}
import services.TokenGenerator
import store.{Passwords, Stores, Users, UsersSupervisor}

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Failure

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

    (stores.users     _)  .expects()      .returning(users)             .once()
    (stores.passwords _)  .expects()      .returning(passwords)         .once()
    (users.byId       _)  .expects(1) .returning(None.successful()) .once()

    val ref = system.actorOf(UsersSupervisor.props(services, tokens, stores), "test-1")

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

    (stores.users     _)  .expects()      .returning(users)                   .once()
    (stores.passwords _)  .expects()      .returning(passwords)               .once()
    (users.byId       _)  .expects(1) .returning(Some(user).successful()) .once()

    val ref = system.actorOf(UsersSupervisor.props(services, tokens, stores), "test-2")

    within(1 seconds) {
      ref ! GetById(1)
      expectMsg(user)
    }
  }

  it should "create a new user without password" in {
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

    (stores.users     _)  .expects()                .returning(users)                 .once()
    (stores.passwords _)  .expects()                .returning(passwords)             .once()
    (users.byUsername _)  .expects("username")  .returning(None.successful())     .once()
    (users.create     _)  .expects(request)         .returning(user.successful())     .once()
    (passwords.create _)  .expects(pwdRequest)      .returning(password.successful()) .once()

    services.secrets().generate _ expects 16 returning "rnd16" once()
    services.rnd().generate     _ expects 32 returning "rnd32" once()

    val ref = system.actorOf(UsersSupervisor.props(services, tokens, stores), "test-3")
    within(1 second) {
      ref ! request
      expectMsg(user.copy(password = Some(password.copy(method = "plain"))))
    }
  }

  it should "create a new user with strong password" in {
    val tokens     = mock[TokenGenerator]
    val services   = mockServices(system)
    val users      = mock[Users]
    val passwords  = mock[Passwords]
    val stores     = mock[Stores]
    val now        = Date.from(services.clock().instant())
    val user       = User(1, 1, now, None, active = true, "username", "email", "type", None)
    val request    = CreateUserRequest(user.account, user.username, Some("strong"), user.email, user.`type`)
    val password   = Password(1, 1, now, null, "sha256", "strong", "rnd32")
    val pwdRequest = CreatePasswordRequest(user.id, "sha256", "strong".sha256(), "rnd32")

    (stores.users     _)  .expects()                .returning(users)                 .once()
    (stores.passwords _)  .expects()                .returning(passwords)             .once()
    (users.byUsername _)  .expects("username")  .returning(None.successful())     .once()
    (users.create     _)  .expects(request)         .returning(user.successful())     .once()
    (passwords.create _)  .expects(pwdRequest)      .returning(password.successful()) .once()

    services.secrets().validate _ expects "strong" returning true once()
    services.secrets().generate _ expects 16 never()
    services.rnd().generate     _ expects 32 returning "rnd32" once()

    val ref = system.actorOf(UsersSupervisor.props(services, tokens, stores), "test-4")
    within(1 minute) {
      ref ! request
      expectMsg(user.copy(password = Some(password.copy(method = "plain"))))
    }
  }

  it should "create a new user with weak password" in {
    val tokens     = mock[TokenGenerator]
    val services   = mockServices(system)
    val users      = mock[Users]
    val passwords  = mock[Passwords]
    val stores     = mock[Stores]
    val now        = Date.from(services.clock().instant())
    val user       = User(1, 1, now, None, active = true, "username", "email", "type", None)
    val request    = CreateUserRequest(user.account, user.username, Some("weak"), user.email, user.`type`)

    (stores.users     _)  .expects()                .returning(users)                 .once()
    (stores.passwords _)  .expects()                .returning(passwords)             .once()
    (users.byUsername _)  .expects("username")  .returning(None.successful())     .once()
    (users.create     _)  .expects(*) .never()
    (passwords.create _)  .expects(*) .never()

    services.secrets().validate _ expects "weak" returning false once()
    services.secrets().generate _ expects 16 never()
    services.rnd().generate     _ expects 32 never()

    val ref = system.actorOf(UsersSupervisor.props(services, tokens, stores), "test-5")
    within(1 minute) {
      ref ! request
      receiveOne(500 millis) match {
        case Failure(e) => e.getMessage shouldBe "Weak Password"
        case _ => fail
      }
    }
  }

}
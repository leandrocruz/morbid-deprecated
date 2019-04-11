package store

import java.sql.Timestamp
import java.time.temporal.ChronoUnit
import java.util.Date

import akka.actor.{Actor, ActorLogging, ActorRef, Props, ReceiveTimeout, Timers}
import domain._
import domain.collections._
import domain.tuples._
import play.api.Configuration
import services.{AppServices, TokenGenerator}
import slick.jdbc.PostgresProfile.api._
import xingu.commons.play.akka.XinguActor
import xingu.commons.utils._

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

trait Users extends ObjectStore[User, CreateUserRequest] {
  def byToken(it: String)          : Future[Option[User]]
  def byUsername(username: String) : Future[Option[User]]
}

object Users {
  private def filter(row: (User, Option[Password], Option[Permission])): Boolean = row match {
      case (user, pwd, perm) =>
        user.deleted.isEmpty &&
        user.active          &&
        pwd.exists  (_.deleted.isEmpty) &&
        perm.exists (_.deleted.isEmpty)
    }

  private def perms(items: Seq[(User, Option[Password], Option[Permission])]): Option[Seq[Permission]] =
    Some {
      items.map({ case (_, _, perm) => perm.get })
    }

  def merge(items: Seq[(User, Option[Password], Option[Permission])]) = {
    val filtered = items.filter { filter }
    filtered
      .headOption
      .map {
        case (user, pwd, _) => user.copy(password = pwd, permissions = perms(filtered))
      }
  }
}

class DatabaseUsers (services: AppServices, db: Database, tokens: TokenGenerator) extends Users {

  type RowFilterParams = ((UserTable, Rep[Option[SecretTable]]), Rep[Option[PermissionTable]]) // remove warning from intellij

  implicit val ec = services.ec()

  def selectOne(rowFilter: RowFilterParams => Rep[Boolean]): Future[Option[User]] = {
    val query = for {
      ((user, secret), perm) <- users joinLeft secrets on { _.id === _.user } joinLeft permissions on { _._1.id === _.user } filter { rowFilter }
    } yield (user, secret, perm)

    db.run(query.result) map { rows =>
      Users.merge {
        rows.map(row => (toUser(row._1), toPassword(row._2), toPermission(row._3)))
      }
    }
  }

  override def byId       (it: Long)   : Future[Option[User]] = selectOne { case ((user, _), _)   => user.id       === it }
  override def byUsername (it: String) : Future[Option[User]] = selectOne { case ((user, _), _)   => user.username === it }
  override def byToken    (it: String) : Future[Option[User]] = selectOne { case ((_, secret), _) =>
    secret.map(value => value.token === it && value.deleted.isEmpty) getOrElse false
  }

  override def create(request: CreateUserRequest): Future[User] = {
    val instant = services.clock().instant()
    val created = new Timestamp(instant.toEpochMilli)

    db.run {
      (users returning users.map(_.id)) += (
        0l,
        request.account,
        created,
        null,
        true,
        request.username,
        request.email,
        request.`type`
      )
    } map { id =>
      User(
        id          = id,
        account     = request.account,
        created     = Date.from(instant),
        deleted     = None,
        active      = true,
        username    = request.username,
        email       = request.email,
        `type`      = request.`type`,
        password    = None,
        permissions = None)
    }
  }
}

object UsersSupervisor {
  def props(
    services : AppServices,
    tokens   : TokenGenerator,
    stores   : Stores) = Props(classOf[UsersSupervisor], services, tokens, stores)
}

case object Refresh
case class DecommissionSupervisor(user: User, replyTo: Option[ActorRef])

class UsersSupervisor (
  services : AppServices,
  tokens   : TokenGenerator,
  stores   : Stores) extends Actor with ActorLogging with XinguActor with Timers {

  implicit val ec = services.ec()

  val users     = stores.users()
  val passwords = stores.passwords()

  val byUsername = mutable.Map[String, ActorRef]()
  val byToken    = mutable.Map[String, ActorRef]()
  val byId       = mutable.Map[Long  , ActorRef]()

  val unknownUser = context.actorOf(Props(classOf[UnknownUserSupervisor]), "unknown-user")

  def addToCache(user: User, ref: ActorRef): ActorRef = {
    byUsername += (user.username -> ref)
    byId       += (user.id -> ref)
    user.password foreach { p => byToken += (p.token -> ref) }
    ref
  }

  def removeFromCache(user: User): Option[ActorRef] = {
    val ref = byUsername remove user.username
    byId remove user.id
    user.password foreach { p => byToken remove p.token}
    ref
  }

  def registerSupervisor(op: Option[User]): ActorRef =
    op match {
      case Some(user) if !byId.contains(user.id) =>
        log.info(s"Creating Supervisor for ${user.id}/${user.password.map(_.token)}")
        val ref = context.actorOf(Props(classOf[SingleUserSupervisor], user, services, tokens, stores), s"user-${user.id}")
        addToCache(user, ref)
      case _ => unknownUser
    }


  def fw(msg: Any, opt: Option[ActorRef], retrieve: => Future[Option[User]]) =
    opt match {
      case Some(ref) => ref forward msg
      case None      =>
        val replyTo = sender()
       retrieve
          .map     { registerSupervisor                       }
          .map     { ref => ref.tell(msg, replyTo)            }
          .recover { case NonFatal(e) => replyTo ! Failure(e) }
    }


  def validatePassword(it: CreateUserRequest): Future[Try[String]] = Future.successful {
    it
      .password
      .map { pwd =>
        if(services.secrets().validate(pwd)) Success(pwd) else Failure(new Exception("Weak Password"))
      }
      .getOrElse {
        val generated = services.secrets().generate(16)
        Success(generated)
      }
  }

  def create(req: CreateUserRequest, same: Option[User], password: Try[String]): Future[Any] =
    (same, password) match {
      case (Some(_), _)         => ResourceAlreadyExists.successful()
      case (_, Failure(e))      => Failure(e).successful()
      case (_, Success(strong)) =>
        val token  = services.rnd().generate(32)
        for {
          u <- users     create req
          p <- passwords create CreatePasswordRequest(u.id, "sha256", strong.sha256(), token)
        } yield u.copy(password = Some(p.copy(password = strong, method = "plain")))
    }

  override def receive = {
    case DecommissionSupervisor(user, replyTo) =>
      log.info(s"Decommissioning supervisor for '${user.username}'")
      removeFromCache(user) foreach { context stop }
      replyTo foreach { _ ! Decommissioned }

    case it @ GetById(id) =>
      fw(it, byId.get(id), users.byId(id))

    case it @ GetByToken(token) =>
      fw(it, byToken.get(token), users.byToken(token))

    case it @ AuthenticateRequest(username, _) =>
      fw(it, byUsername.get(username), users.byUsername(username))

    case it : CreateUserRequest =>
      to(sender()) {
        for {
          pwd  <- validatePassword(it)
          same <- users.byUsername(it.username)
          user <- create(it, same, pwd)
        } yield user
      }

    case it @ ResetPasswordRequest(Some(username), _) =>
      fw(it, byUsername.get(username), users.byUsername(username))

    case it @ RefreshUserRequest(id) =>
      fw(it, byId.get(id), users.byId(id))

    case any =>
      log.error(s"Can't handle $any")
  }
}

class UnknownUserSupervisor extends Actor {
  override def receive = {
    case _ => sender ! UnknownUser
  }
}

class SingleUserSupervisor (
  user           : User,
  services       : AppServices,
  tokens         : TokenGenerator,
  accountManager : Stores) extends Actor with ActorLogging with XinguActor {

  implicit val ec  = services.ec()
  val conf         = services.conf().get[Configuration] ("passwords")
  val expiresIn    = Some(conf.get[Duration]            ("tokens.expiresIn"))
  val issuer       = conf.get[String]                   ("tokens.issuer")
  val notOlderThan = conf.get[Int]                      ("mustBe.notOlderThan")
  val daysAgo      = java.time.Duration.of(notOlderThan, ChronoUnit.DAYS)

  context.setReceiveTimeout(5 minutes)
  //  timers.startPeriodicTimer("refresh", Refresh, 1 seconds)

  def passwordLifetimeLimit() = Date.from {
    services.clock().instant().minus(daysAgo)
  }

  def checkPassword(provided: String)(password: Password): Try[Token] = {
    if(password.created.before(passwordLifetimeLimit())) {
      Failure { new Exception("Password Too Old") }
    } else if (provided.sha256 == password.password)
      Success { tokens.createToken(issuer, password.token, expiresIn) }
    else
      Failure { new Exception("Password Mismatch") }
  }

  def authenticate(password: String) =
    if("crash" == password) {
      throw new Exception("crash")
    } else {
      user.password map {
        checkPassword(password)
      } getOrElse {
        Failure { new Exception("No Password Available") }
      }
    }

  def resetPasswordFor(username: String): Future[Any] = {
    Future.successful("ok")
  }

  def decommission(replyTo: Option[ActorRef]) = {
    context.parent ! DecommissionSupervisor(user, replyTo)
  }

  override def receive = {
    case ReceiveTimeout                          => decommission(None)
    case RefreshUserRequest(_)                   => decommission(Some(sender))
    case AuthenticateRequest(_, password)        => sender ! authenticate(password)
    case GetByToken(_)                           => sender ! user
    case GetById(_)                              => sender ! user
    case ResetPasswordRequest(Some(username), _) => to(sender) { resetPasswordFor(username) }
    case any                                     => log.error(s"Can't handle $any")
  }
}
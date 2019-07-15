package store

import java.sql.Timestamp
import java.time.temporal.ChronoUnit
import java.util.Date

import akka.actor.{Actor, ActorLogging, ActorRef, Props, ReceiveTimeout, Timers}
import cats.data.EitherT
import cats.implicits._
import domain._
import domain.collections._
import domain.tuples._
import play.api.Configuration
import services.{AppServices, TokenGenerator}
import slick.jdbc.PostgresProfile.api._
import store.violations._
import xingu.commons.play.akka.XinguActor
import xingu.commons.utils._

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.control.NonFatal
import scala.util.{Either, Failure}

trait Users extends ObjectStore[User, CreateUserRequest] {
  def byToken(it: String)    : Future[Option[User]]
  def byEmail(email: String) : Future[Option[User]]
}

object Users {
  private def filter(row: (User, Account, Option[Password], Option[Permission])): Boolean = row match {
      case (user, account, pwd, perm) =>
        user.deleted.isEmpty && user.active             &&
        account.active       && account.deleted.isEmpty &&
        pwd .forall (_.deleted.isEmpty) &&
        perm.forall (_.deleted.isEmpty)
    }

  private def perms(items: Seq[(User, Account, Option[Password], Option[Permission])]): Option[Seq[Permission]] = {
    val perms = items
      .map({ case (_, _, _, p) => p })
      .filter(_.isDefined)
      .map(_.get)

    if(perms.isEmpty) None else Some(perms)
  }

  def merge(items: Seq[(User, Account, Option[Password], Option[Permission])]) = {
    val filtered = items.filter { filter }
    filtered
      .headOption
      .map {
        case (user, account, pwd, _) => user.copy(password = pwd, account = Some(account), permissions = perms(filtered))
      }
  }
}

class DatabaseUsers (services: AppServices, db: Database, tokens: TokenGenerator) extends Users {

  type RowFilterParams = (((UserTable, AccountTable), Rep[Option[SecretTable]]), Rep[Option[PermissionTable]]) // remove warning from intellij

  implicit val ec = services.ec()

  def selectOne(rowFilter: RowFilterParams => Rep[Boolean]): Future[Option[User]] = {
    val query = for {
      (((user, account), secret), perm) <- users join accounts on { _.account === _.id } joinLeft secrets on { _._1.id === _.user } joinLeft permissions on { _._1._1.id === _.user } filter { rowFilter }
    } yield (user, account, secret, perm)

    //println(query.result.statements)

    db.run(query.result) map { rows =>
      Users.merge {
        rows.map(row => (toUser(row._1), toAccount(row._2), toPassword(row._3), toPermission(row._4)))
      }
    }
  }

  override def byId    (it: Long)   : Future[Option[User]] = selectOne { case (((user, _), _), _)   => user.id    === it }
  override def byEmail (it: String) : Future[Option[User]] = selectOne { case (((user, _), _), _)   => user.email === it }
  override def byToken (it: String) : Future[Option[User]] = selectOne { case ((_, secret), _)      =>
    secret.map(value => value.token === it && value.deleted.isEmpty) getOrElse false
  }

  override def create(request: CreateUserRequest): Future[Either[Violation, User]] = {
    val instant = services.clock().instant()
    val created = new Timestamp(instant.toEpochMilli)

    db.run {
      (users returning users.map(_.id)) += (
        0l,
        request.account,
        created,
        null,
        true,
        request.name,
        request.email,
        request.`type`
      )
    } map { id =>
      Right(
        User(
          id          = id,
          account     = None,
          created     = Date.from(instant),
          deleted     = None,
          active      = true,
          name        = request.name,
          email       = request.email,
          `type`      = request.`type`,
          password    = None,
          permissions = None
      ))
    } recover {
      case NonFatal(e) => Left(Violations.of(e))
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

  val byEmail   = mutable.Map[String, ActorRef]()
  val byToken   = mutable.Map[String, ActorRef]()
  val byId      = mutable.Map[Long  , ActorRef]()

  val unknownUser = context.actorOf(Props(classOf[UnknownUserSupervisor]), "unknown-user")

  def addToCache(user: User, ref: ActorRef): ActorRef = {
    byEmail += (user.email -> ref)
    byId    += (user.id    -> ref)
    user.password foreach { p => byToken += (p.token -> ref) }
    ref
  }

  def removeFromCache(user: User): Option[ActorRef] = {
    val ref = byEmail remove user.email
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


  def create(req: CreateUserRequest, same: Option[User]): Future[Either[Violation, User]] =
    same match {
      case Some(_)  => Left(UniqueViolation(new Exception("User Already Exists"))).successful()
      case None     =>
        val token    = services.rnd().generate(32)
        val password = services.secrets().generate(16)
        val it = for {
          u <- EitherT(users     create req)
          p <- EitherT(passwords create CreatePasswordRequest(u.id, "sha256", password.sha256(), token, forceUpdate = true))
        } yield u.copy(password = Some(p.copy(password = password, method = "plain")))
        it.value
    }

  override def receive = {
    case DecommissionSupervisor(user, replyTo) =>
      log.info(s"Decommissioning supervisor for '${user.email}'")
      removeFromCache(user) foreach { context stop }
      replyTo foreach { _ ! Done }

    case it @ GetById(id) =>
      fw(it, byId.get(id), users.byId(id))

    case it @ GetByToken(token) =>
      fw(it, byToken.get(token), users.byToken(token))

    case it @ AuthenticateRequest(email, _) =>
      fw(it, byEmail.get(email), users.byEmail(email))

    case it @ AssignPermissionRequest(id, _) =>
      fw(it, byId.get(id), users.byId(id))

    case it : CreateUserRequest =>
      to(sender()) {
        for {
          same <- users.byEmail(it.email)
          user <- create(it, same)
        } yield user
      }

    case it @ ResetPasswordRequest(email) =>
      fw(it, byEmail.get(email), users.byEmail(email))

    case it @ RefreshUserRequest(id) =>
      fw(it, byId.get(id), users.byId(id))

    case it @ ChangePasswordRequest(email, _, _) =>
      fw(it, byEmail.get(email), users.byEmail(email))

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
  var user : User,
  services : AppServices,
  tokens   : TokenGenerator,
  stores   : Stores) extends Actor with ActorLogging with XinguActor {

  import domain.Done

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

  def checkPassword(provided: String)(password: Password): Either[Violation, Token] = {
    val old  = password.created.before(passwordLifetimeLimit())
    val same = provided.sha256 == password.password

    (old, same) match {
      case (_, false)    => Left(PasswordMismatch)
      case (true , true) => Left(PasswordTooOld)
      case (false, true) => Right(tokens.createToken(issuer, password.token, expiresIn))
    }
  }

  def createNewPassword(replacement: String) = {
    val passwords = stores.passwords()
    val token     = services.rnd().generate(32)
    passwords create CreatePasswordRequest(user.id, "sha256", replacement.sha256(), token)
  }

  def changePassword(old: String, replacement: String): Future[Either[Violation, Done.type]] = {
    val pwd = user.password.get
    checkPassword(old)(pwd) match {
      case Left(PasswordMismatch) => Left(PasswordMismatch).successful()
      case _ =>
        //TODO: check if replacement is the same as the 3 last passwords
        val same   = old == replacement
        val strong = services.secrets().validate(replacement)

        if (same)         Left(PasswordAlreadyUsed) .successful()
        else if (!strong) Left(PasswordTooWeak)     .successful()
        else              EitherT(createNewPassword(replacement)) map {
          pwd =>
            /* Ouch! This is ugly */ user = user.copy(password = Some(pwd))
            Done
        } value
    }
  }

    def authenticate(password: String) =
      if("crash" == password) {
        throw new Exception("crash")
      } else {
        user.password map {
          checkPassword(password)
        } getOrElse {
          Left(NoPasswordAvailable)
        }
      }

  def resetPasswordFor(email: String): Future[Any] = {
    Future.successful("ok")
  }

  def decommission(replyTo: Option[ActorRef]) = {
    context.parent ! DecommissionSupervisor(user, replyTo)
  }

  def update(perm: Permission) = {
    val perms = user.permissions.map(_ :+ perm).orElse(Some(Seq(perm)))
    user = user.copy(permissions = perms)
    Done
  }

  def assignPermission(permission: String): Future[Either[Violation, Done.type]] =
    stores
      .permissions()
      .create(AssignPermissionRequest(user.id, permission))
      .map(_.map(update))


  override def receive = {
    case ReceiveTimeout                             => decommission(None)
    case RefreshUserRequest(_)                      => decommission(Some(sender))
    case AuthenticateRequest(_, password)           => sender ! authenticate(password)
    case GetByToken(_)                              => sender ! user
    case GetById(_)                                 => sender ! user
    case ResetPasswordRequest(email)                => to(sender) { resetPasswordFor(email)          }
    case AssignPermissionRequest(_, permission)     => to(sender) { assignPermission(permission)     }
    case ChangePasswordRequest(_, old, replacement) => to(sender) { changePassword(old, replacement) }
    case any                                        => log.error(s"Can't handle $any")
  }
}
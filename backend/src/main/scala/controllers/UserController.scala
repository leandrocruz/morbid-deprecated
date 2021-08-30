package controllers

import domain._
import domain.json._
import javax.inject.Inject
import play.api.libs.json._
import play.api.mvc.Result
import services.{AppServices, TokenGenerator}
import shapeless.TypeCase
import store.{RootActors, Stores, Violation}
import xingu.commons.play.akka.utils._
import xingu.commons.utils._

import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

class UserController @Inject()(
  services : AppServices,
  actors   : RootActors,
  tokens   : TokenGenerator,
  stores   : Stores) extends ControllerSupport (services) {

  val SuccessToken = TypeCase[Success[Token]]

  def create() = Action.async(parse.json) { implicit r =>
    createResource[User, CreateUserRequest](actors.users())
  }

  def update() = Action.async(parse.json) { implicit r =>
    createResource[User, UpdateUserRequest](actors.users())
  }

  def serialize(user: User): JsValue = Json.toJson(user)
  def skip[T](t: T) = JsNull

  def toResult[T](fut: Future[Any]): Future[Result] = toJson(skip) { fut }

  def toJson[T](fn: T => JsValue)(fut: Future[Any]): Future[Result] = fut map {
    case Left(v: Violation) => violationToResult(v)
    case UnknownUser        => NotFound("UnknownUser")
    case Failure(e)         => log.error("Error", e); Forbidden(e.getMessage)
    case Right(value: T)    => Ok(fn(value))
    case value: T           => Ok(fn(value))
    case Done               => Ok
  } recover {
    case NonFatal(e) => log.error("", e); InternalServerError
  }

  def byId(it: Long)= Action.async {
    toJson[User](serialize) {
      inquire(actors.users()) { GetById(it) }
    }
  }

  def byEmail(it: String)= Action.async {
    toJson[User](serialize) {
      inquire(actors.users()) { GetByEmail(it) }
    }
  }

  def byToken(it: String) = Action.async {
    tokens.verify(it) match {
      case Failure(e)   => Forbidden("Invalid Signature").successful()
      case Success(jws) =>
        toJson[User](serialize) {
          inquire(actors.users()) { GetByToken(jws.getBody.getSubject) }
        }
    }
  }

  def login() = Action.async (parse.json) { implicit r =>
    validateThen[AuthenticateRequest] { req =>
      toJson[Token](tk => Json.toJson(tk)) {
        inquire(actors.users()) { req }
      }
    }
  }

  def resetPassword() = Action.async(parse.json) { implicit r =>
    validateThen[ResetPasswordRequest] { req =>
      toJson[User](serialize) {
        inquire(actors.users()) { req }
      }
    }
  }

  def refresh() = Action.async(parse.json) { implicit r =>
    validateThen[RefreshUserRequest] { req =>
      toResult {
        inquire(actors.users()) { req }
      }
    }
  }

  def changePassword() = Action.async(parse.json) { implicit r =>
    validateThen[ChangePasswordRequest] { req =>
      toResult {
        inquire(actors.users()) { req }
      }
    }
  }

  def forcePassword() = Action.async(parse.json) { implicit r =>
    validateThen[ForcePasswordRequest] { req =>
      toResult {
        inquire(actors.users()) { req }
      }
    }
  }

  def assignPermission() = Action.async(parse.json) { implicit r =>
    validateThen[AssignPermissionRequest] { req =>
      toResult {
        inquire(actors.users()) { req }
      }
    }
  }

  def impersonate() = Action.async(parse.json) { implicit r =>
    validateThen[ImpersonateRequest] { req =>
      inquire(actors.users()) { req } map {
        case Some(token: Token) => Ok(Json.toJson(token))
        case None => NotImplemented
      }
    }
  }

  def byAccount(account: Long) = Action.async {
    inquire(actors.users()) { ByAccount(account) } map {
      case Left(e: Throwable)      => log.error("Error", e); InternalServerError(e.getMessage)
      case Right(users: Seq[User]) => Ok(Json.toJson(users))
    }
  }

  def deleteUser(account: Long, user: Long) = Action.async {
    inquire(actors.users()) { DeleteUser(account, user) } map {
      case Left(e: Throwable) => log.error("Error", e); InternalServerError(e.getMessage)
      case Right(_)           => Ok

    }
  }
}
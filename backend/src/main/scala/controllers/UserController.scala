package controllers

import domain._
import domain.json._
import javax.inject.Inject
import play.api.libs.json._
import play.api.mvc.Result
import services.{AppServices, TokenGenerator}
import shapeless.TypeCase
import store.violations.{PasswordAlreadyUsed, PasswordMismatch, PasswordTooOld, PasswordTooWeak}
import store.{RootActors, Stores, Violation}
import xingu.commons.utils._
import xingu.commons.play.akka.utils._

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

  def serialize(user: User): JsValue = Json.toJson(user)
  def skip[T](t: T) = JsNull

  def toResult[T](fut: Future[Any]): Future[Result] = toJson(skip) { fut }

  def toJson[T](fn: T => JsValue)(fut: Future[Any]): Future[Result] = fut map {
    case UnknownUser           => NotFound
    case Failure(e)            => log.error("Error", e); Forbidden(e.getMessage)
    case Left(v: Violation)    => v match {
      case PasswordTooOld      => Unauthorized("PasswordTooOld")
      case PasswordMismatch    => Forbidden("PasswordMismatch")
      case PasswordAlreadyUsed => BadRequest("PasswordAlreadyUsed")
      case PasswordTooWeak     => BadRequest("PasswordTooWeak")
      case _                   => log.error(s"Violation: '$v'"); InternalServerError
    }
    case Right(value: T)       => Ok(fn(value))
    case value: T              => Ok(fn(value))
    case Done                  => Ok
  } recover {
    case NonFatal(e) => log.error("", e); InternalServerError
  }

  def byId(it: Long)= Action.async {
    toJson[User](serialize) {
      inquire(actors.users()) { GetById(it) }
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
    validateThen[ResetPasswordRequest] {
      case ResetPasswordRequest(None, None) =>
        Future.successful(BadRequest)
      case req =>
        toResult {
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

  def assignPermission() = Action.async(parse.json) { implicit r =>
    validateThen[AssignPermissionRequest] { req =>
      toResult {
        inquire(actors.users()) { req }
      }
    }
  }
}
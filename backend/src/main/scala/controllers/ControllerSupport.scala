package controllers

import akka.actor.ActorRef
import org.apache.commons.lang3.exception.ExceptionUtils
import org.slf4j.LoggerFactory
import play.api.libs.json._
import play.api.mvc.{InjectedController, Request, Result}
import services.AppServices
import store.{ObjectStore, Violation}
import store.violations._
import xingu.commons.utils._
import xingu.commons.play.akka.utils._
import xingu.commons.play.controllers.XinguController

import scala.concurrent.Future
import scala.util.control.NonFatal

class ControllerSupport (services: AppServices) extends InjectedController with XinguController {

  implicit val ec = services.ec()
  implicit val system = services.actorSystem()
  val log = LoggerFactory.getLogger(getClass)

  def violationToResult(violation: Violation) = violation match {
    case PasswordMismatch                 =>                             Forbidden            ("PasswordMismatch")
    case PasswordTooOld                   =>                             Unauthorized         ("PasswordTooOld")
    case NoPasswordAvailable              =>                             Unauthorized         ("NoPasswordAvailable")
    case PasswordAlreadyUsed              =>                             PreconditionFailed   ("PasswordAlreadyUsed")
    case PasswordTooWeak                  =>                             PreconditionFailed   ("PasswordTooWeak")
    case ForeignKeyViolation          (e) => log.error(s"Violation", e); PreconditionFailed   ("ForeignKeyViolation")
    case IntegrityConstraintViolation (e) => log.error(s"Violation", e); PreconditionFailed   ("IntegrityConstraintViolation")
    case UniqueViolation              (e) => log.error(s"Violation", e); Conflict             ("UniqueViolation")
    case UnknownViolation             (e) => log.error(s"Violation", e); InternalServerError  ("UnknownViolation")
  }

  def handle[R](it: Any)(implicit writer: Writes[R]): Result = it match {
    case Right(resource: R)         => Ok(Json.toJson(resource))
    case Left(violation: Violation) => violationToResult(violation)
    case Left(err: Throwable)       => InternalServerError(ExceptionUtils.getStackTrace(err))
  }

  def createResource[RESOURCE, CREATE](actor: ActorRef)(fn: CREATE => CREATE)(implicit req: Request[JsValue], writer: Writes[RESOURCE], reader: Reads[CREATE]): Future[Result] = {
    req.body.validate[CREATE] match {
      case success: JsSuccess[CREATE] =>
        inquire(actor) { fn(success.get) } map handle[RESOURCE] recover {
          case NonFatal(e) => log.error("Error Creating Resource (recover)", e); InternalServerError
        }
      case JsError(err)    => BadRequest("Error Creating Resource: " + JsError.toJson(err)).successful()
    }
  }

  def createResourceDirectly[RESOURCE, REQUEST](collection: ObjectStore[RESOURCE, REQUEST])(implicit req: Request[JsValue], writer: Writes[RESOURCE], reader: Reads[REQUEST]): Future[Result] = {
    validateThen[REQUEST] { it =>
      collection.create(it) map handle[RESOURCE] recover {
        case NonFatal(e)   => log.error("Error Creating Resource", e); InternalServerError
      }
    }
  }
}
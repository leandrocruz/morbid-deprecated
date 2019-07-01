package controllers

import akka.actor.ActorRef
import org.slf4j.LoggerFactory
import play.api.libs.json._
import play.api.mvc.{InjectedController, Request, Result}
import services.AppServices
import store.ObjectStore
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


  def handle[R](it: Any)(implicit writer: Writes[R]): Result = it match {
    case Right(resource: R) => Ok(Json.toJson(resource))
    case Left(violation)    => violation match {
      case IntegrityConstraintViolation (e) => log.error("IntegrityConstraintViolation", e) ; PreconditionFailed("IntegrityConstraintViolation")
      case ForeignKeyViolation          (e) => log.error("ForeignKeyViolation", e)          ; PreconditionFailed("ForeignKeyViolation")
      case UniqueViolation              (e) => log.error("UniqueViolation", e)              ; Conflict("UniqueViolation")
      case UnknownViolation             (e) => log.error("UnknownViolation", e)             ; InternalServerError("UnknownViolation")
    }
  }

  def createResource[RESOURCE, CREATE](actor: ActorRef)(implicit req: Request[JsValue], writer: Writes[RESOURCE], reader: Reads[CREATE]): Future[Result] =
    req.body.validate[CREATE] match {
      case success: JsSuccess[CREATE] =>
        inquire(actor) { success.get } map handle[RESOURCE] recover {
          case NonFatal(e) => log.error("Error Creating Resource (recover)", e); InternalServerError
        }
      case JsError(err)    => BadRequest(JsError.toJson(err)).successful()
    }

  def createResourceDirectly[RESOURCE, REQUEST](collection: ObjectStore[RESOURCE, REQUEST])(implicit req: Request[JsValue], writer: Writes[RESOURCE], reader: Reads[REQUEST]): Future[Result] = {
    validateThen[REQUEST] { it =>
      collection.create(it) map handle[RESOURCE] recover {
        case NonFatal(e)   => log.error("Error Creating Resource", e); InternalServerError
      }
    }
  }
}
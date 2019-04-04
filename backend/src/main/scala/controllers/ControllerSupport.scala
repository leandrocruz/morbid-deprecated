package controllers

import akka.actor.ActorRef
import domain._
import org.slf4j.LoggerFactory
import play.api.libs.json._
import play.api.mvc.{InjectedController, Request, Result}
import services.AppServices
import store.ObjectStore
import xingu.commons.play.akka.utils._
import xingu.commons.play.controllers.XinguController

import scala.concurrent.Future
import scala.util.Failure
import scala.util.control.NonFatal

class ControllerSupport (services: AppServices) extends InjectedController with XinguController {

  implicit val ec = services.ec()
  implicit val system = services.actorSystem()
  val log = LoggerFactory.getLogger(getClass)

  def createResource[RESOURCE, CREATE](actor: ActorRef)(implicit req: Request[JsValue], writer: Writes[RESOURCE], reader: Reads[CREATE]): Future[Result] =
    createResource[RESOURCE, CREATE](actor, withPayload = true)(req, writer, reader)

  def createResource[RESOURCE, CREATE](actor: ActorRef, withPayload: Boolean)(implicit req: Request[JsValue], writer: Writes[RESOURCE], reader: Reads[CREATE]): Future[Result] =
    req.body.validate[CREATE] match {
      case success: JsSuccess[CREATE] =>
        inquire(actor) { success.get } map {
          case ResourceAlreadyExists => Conflict("Resource Already Exists")
          case Failure(e)            => log.error("Error Creating Resource", e); InternalServerError
          case resource: RESOURCE    => if(withPayload) Ok(Json.toJson(resource)) else Ok
        } recover {
          case NonFatal(e) => log.error("Error Creating Resource", e); InternalServerError
        }
      case JsError(err) =>
        Future.successful(BadRequest(JsError.toJson(err).toString()))
    }

  def createResourceDirectly[RESOURCE, REQUEST](collection: ObjectStore[RESOURCE, REQUEST])(implicit req: Request[JsValue], writer: Writes[RESOURCE], reader: Reads[REQUEST]): Future[Result] = {
    validateThen[REQUEST] { it =>
      collection.create(it) map {
        resource: RESOURCE => Ok(Json.toJson(resource))
      } recover {
        case NonFatal(e)   => log.error("Error Creating Resource", e); InternalServerError
      }
    }
  }
}
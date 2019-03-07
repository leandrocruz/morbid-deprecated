package controllers

import akka.Inquire._
import akka.actor.ActorRef
import domain._
import org.slf4j.LoggerFactory
import play.api.libs.json._
import play.api.mvc.{InjectedController, Request, Result}
import services.Services
import store.ObjectStore

import scala.concurrent.Future
import scala.util.Failure
import scala.util.control.NonFatal

class ControllerSupport (services: Services) extends InjectedController {

  implicit val ec = services.ec()
  implicit val system = services.actorSystem()
  val log = LoggerFactory.getLogger(getClass)

  def onJsErr(e: JsError) =
    Future.successful(BadRequest(JsError.toJson(e).toString()))

  def toResult[T](f: Future[Option[T]])(implicit writer: OWrites[T]): Future[Result] =
    f map {
      _.map { it => Ok(Json.toJson(it)) } getOrElse { NotFound }
    } recover {
      case NonFatal(e) => log.error("", e); InternalServerError
    }

  def createResource[RESOURCE, CREATE](req: Request[JsValue], actor: ActorRef)(implicit writer: Writes[RESOURCE], reader: Reads[CREATE]): Future[Result] =
    req.body.validate[CREATE] match {
      case success: JsSuccess[CREATE] =>
        inquire(actor) { success.get } map {
          case ResourceAlreadyExists => Conflict("Resource Already Exists")
          case Failure(e)            => log.error("Error Creating Resource", e); InternalServerError
          case resource: RESOURCE    => Ok(Json.toJson(resource))
        } recover {
          case NonFatal(e) => log.error("Error Creating Resource", e); InternalServerError
        }
      case JsError(err)    => Future.successful(BadRequest)
    }

  def createResourceDirectly[RESOURCE, REQUEST](req: Request[JsValue], collection: ObjectStore[RESOURCE, REQUEST])(implicit writer: Writes[RESOURCE], reader: Reads[REQUEST]): Future[Result] =
    req.body.validate[REQUEST] match {
      case success: JsSuccess[REQUEST] =>
        collection.create(success.get) map {
          resource: RESOURCE => Ok(Json.toJson(resource))
        } recover {
          case NonFatal(e)   => log.error("Error Creating Resource", e); InternalServerError
        }
      case JsError(err)      => Future.successful(BadRequest)
    }

  def withRequest[T](onSuccess: T => Future[Result])(implicit request: Request[JsValue], reader: Reads[T]): Future[Result] =
    request.body.validate[T] match {
      case e: JsError      => onJsErr(e)
      case s: JsSuccess[T] => onSuccess(s.get)
    }
}
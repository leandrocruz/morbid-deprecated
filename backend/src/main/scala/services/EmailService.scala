package services

import play.api.Logger
import play.api.libs.json.{JsError, JsSuccess, Json}
import play.api.libs.ws.{WSClient, WSResponse}

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future
import scala.concurrent.duration.{Duration, DurationInt}
import scala.util.control.NonFatal

object email {

  sealed trait Email {
    def to: String
    def bcc: Seq[String]
    def subject: String
  }

  case class Attachment(filename: String, content: String)
  case class HtmlEmail(to: String, bcc: Seq[String], subject: String, html: String, attachments: Attachment*) extends Email
  case class TemplateEmail(to: String, bcc: Seq[String], subject: String, templateId: String, data: Map[String, String], attachments: Attachment*) extends Email

  case class EmailResponse(response: String)

  object json {
    implicit val AttachmentFormat    = Json.format[Attachment]
    implicit val HtmlEmailFormat     = Json.format[HtmlEmail]
    implicit val TemplateEmailFormat = Json.format[TemplateEmail]
    implicit val EmailFormat         = Json.format[Email]
    implicit val EmailResponseFormat = Json.format[EmailResponse]
  }

  trait EmailService {
    def sendEmail(email: Email): Future[Either[Throwable, EmailResponse]]
  }

  @Singleton
  class EmailServiceImpl @Inject()(
    services : AppServices,
    ws: WSClient
  ) extends EmailService {

    import json._

    private val base = services.conf().get[String]("email.url")
    private val tm = services.conf().getOptional[Duration]("email.timeout").getOrElse(1 minute)

    private val logger = Logger(getClass)
    private implicit val ec = services.ec()

    def sendEmail(email: Email): Future[Either[Throwable, EmailResponse]] = {

      def parse(res: WSResponse): Either[Throwable, EmailResponse] = {
        res.json.validate[EmailResponse] match {
          case JsSuccess(value, _) => Right(value)
          case JsError(errors) =>
            logger.error(s"Error sending e-mail to '${email.to}': $errors")
            Left(new Exception("Json parser error: " + Json.stringify(JsError.toJson(errors))))
        }
      }

      ws
        .url(s"$base/sendEmail")
        .withRequestTimeout(tm)
        .post(Json.toJson(email))
        .map(parse)
        .recover {
          case NonFatal(e) => Left(e)
        }
    }
  }

}

package services

import domain.User
import play.api.Logger
import services.email._

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

object notification {

  trait NotificationService {
    def notifyTwoFactorAuth(user: User, code: String, timeout: FiniteDuration): Future[Either[Throwable, Unit]]
  }

  @Singleton
  class EmailNotificationService @Inject() (
   services : AppServices,
   emailService: EmailService) extends NotificationService {

    private implicit val ec = services.ec()

    private val subject = services.conf().get[String]("notification.twoAuth.subject")
    private val templateId = services.conf().get[String]("notification.twoAuth.template-email")

    override def notifyTwoFactorAuth(user: User, code: String, timeout: FiniteDuration): Future[Either[Throwable, Unit]] = {
      val data = Map("code" -> code, "email" -> user.email, "timeout" -> s"${timeout.toMinutes}")
      val email = TemplateEmail(user.email, Seq.empty, subject, templateId, data)
      emailService.sendEmail(email).map(_.map(_ => ()))
    }
  }

  @Singleton
  class LogNotificationService () extends NotificationService {

    private val logger = Logger(getClass)

    override def notifyTwoFactorAuth(user: User, code: String, timeout: FiniteDuration): Future[Either[Throwable, Unit]] = {
      logger.info(s"Two Factor Code: $code (${user.email}). This code expires in ${timeout.toMinutes} minutes.")
      Future.successful(Right(()))
    }
  }
}

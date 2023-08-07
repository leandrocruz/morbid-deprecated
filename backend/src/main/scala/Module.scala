import java.time.Clock
import store._
import com.google.inject.AbstractModule
import play.api.{Configuration, Environment}
import services._
import services.email.{EmailService, EmailServiceImpl}
import services.notification.{EmailNotificationService, LogNotificationService, NotificationService}
import services.otp.{OTPGenerator, OTPGeneratorImpl}
import xingu.commons.play.services.{BasicServices, Services}

class Module(env: Environment, conf: Configuration) extends AbstractModule {

  private val notificationMode = conf.getOptional[String]("notification.mode").getOrElse("log")

  override def configure() = {
    bind(classOf[Clock]).toInstance(Clock.systemDefaultZone)
    bind(classOf[Random])             .to(classOf[SimpleRandom])          .asEagerSingleton()
    bind(classOf[SecretValidator])    .to(classOf[PassaySecretValidator]) .asEagerSingleton()
    bind(classOf[Services])           .to(classOf[BasicServices])         .asEagerSingleton()
    bind(classOf[AppServices])        .to(classOf[AppServicesImpl])       .asEagerSingleton()
    bind(classOf[RootActors])         .to(classOf[RootActorsImpl])        .asEagerSingleton()
    bind(classOf[TokenGenerator])     .to(classOf[JJwtTokenGenerator])
    bind(classOf[OTPGenerator])       .to(classOf[OTPGeneratorImpl])
    bind(classOf[EmailService])       .to(classOf[EmailServiceImpl])
    bind(classOf[Stores])             .to(classOf[StoresImpl])

    val notificationClass: Class[_ <: NotificationService] = notificationMode match {
      case "email" => classOf[EmailNotificationService]
      case "log"   => classOf[LogNotificationService]
    }
    bind(classOf[NotificationService]) .to(notificationClass)
  }
}

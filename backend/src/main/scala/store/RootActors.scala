package store

import akka.actor.ActorRef
import services.notification.NotificationService
import services.otp.OTPGenerator

import javax.inject.{Inject, Singleton}
import services.{AppServices, TokenGenerator}

trait RootActors {
  def users(): ActorRef

}

@Singleton
class RootActorsImpl @Inject() (
  services       : AppServices,
  tokens         : TokenGenerator,
  otpGenerator   : OTPGenerator,
  notification   : NotificationService,
  accountManager : Stores) extends RootActors {

  val usersRef = services
    .actorSystem()
    .actorOf(UsersSupervisor.props(services, tokens, otpGenerator, notification, accountManager), "users")

  override def users() = usersRef
}


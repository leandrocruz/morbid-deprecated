package store

import akka.actor.ActorRef
import javax.inject.{Inject, Singleton}
import services.{Services, TokenGenerator}

trait RootActors {
  def users(): ActorRef

}

@Singleton
class RootActorsImpl @Inject() (
  services       : Services,
  tokens         : TokenGenerator,
  accountManager : Stores) extends RootActors {

  val usersRef = services
    .actorSystem()
    .actorOf(UsersSupervisor.props(services, tokens, accountManager), "users")

  override def users() = usersRef
}


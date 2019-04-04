package store

import akka.actor.ActorRef
import javax.inject.{Inject, Singleton}
import services.{AppServices, TokenGenerator}

trait RootActors {
  def users(): ActorRef
}

@Singleton
class RootActorsImpl @Inject() (
  services       : AppServices,
  tokens         : TokenGenerator,
  accountManager : Stores) extends RootActors {

  val usersRef = services
    .actorSystem()
    .actorOf(UsersSupervisor.props(services, tokens, accountManager), "users")

  override def users(): ActorRef = usersRef
}


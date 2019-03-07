package akka

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

import scala.concurrent.{Future, Promise}

object Inquire {
  def inquire(target: ActorRef)(msg: Any)(implicit system: ActorSystem) : Future[Any] = {
    val promise = Promise[Any]
    system.actorOf(Props(classOf[Inquire], target, promise)) ! msg
    promise.future
  }
}

class Inquire(manager: ActorRef, promise: Promise[Any]) extends Actor {

  import context._

  def waitingForReply: Receive = {
    case any =>
      promise.success(any)
      context stop self
  }

  override def receive: Receive = {
    case any =>
      become(waitingForReply)
      manager ! any
  }
}
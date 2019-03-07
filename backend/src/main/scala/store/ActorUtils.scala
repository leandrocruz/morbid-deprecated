package store

import akka.actor.ActorRef

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Failure
import scala.util.control.NonFatal

trait ActorUtils {
  def to(ref: ActorRef)(fn: => Future[Any])(implicit ec: ExecutionContext) =
    fn map { result =>
      ref ! result
    } recover {
      case NonFatal(e) => ref ! Failure(e)
    }
}

package morbid.client

import morbid.client.domain.{CreateUserRequest, Violation}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.util.{Failure, Try}

object Main extends App {
  override def main(args: Array[String]) = {
    val client = new HttpMorbidClientSupport("http://localhost:9004", ExecutionContext.global) {
      def violation[T](response: String): Either[Violation, T] = Left(Violation("UnknownViolation", Some(new Exception(response))))
      def toEither[T](response: String): Either[Throwable, T] = Left(new Exception(response))

      override def userOrViolation    (response: String) = violation(response)
      override def accountOrViolation (response: String) = violation(response)
      override def toUser  (response: String) = toEither(response)
      override def toUsers (response: String) = toEither(response)
      override def toToken (response: String)   = error(response)
    }

    val result = Await.result(client.usersBy(1), 10 seconds)
    println(result)
  }
}

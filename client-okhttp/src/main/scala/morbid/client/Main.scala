package morbid.client

import morbid.client.domain.CreateUserRequest
import morbid.client.violations.UnknownViolation

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.util.{Failure, Try}

object Main extends App {
  override def main(args: Array[String]) = {
    val client = new HttpMorbidClientSupport("http://localhost:9004", ExecutionContext.global) {
      def violation[T](response: String): Either[Violation, T] = {
        println(response)
        Left(UnknownViolation(new Exception("TODO")))
      }
      def error[T](response: String): Try[T] = {
        println(response)
        Failure(new Exception("TODO"))
      }

      override def userOrViolation    (response: String) = violation(response)
      override def accountOrViolation (response: String) = violation(response)
      override def toUser  (response: String) = error(response)
      override def toToken (response: String) = error(response)
    }

    val result = Await.result(client.createUser(CreateUserRequest(4, "test", None, "email", "type")), 10 seconds)
    println(result)
  }
}

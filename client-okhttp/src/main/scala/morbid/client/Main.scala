package morbid.client

import morbid.client.domain.{CreateAccountRequest, CreateUserRequest}

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._
import scala.util.Failure

object Main extends App {
  override def main(args: Array[String]) = {
    val client = new HttpMorbidClientSupport("http://localhost:9004", ExecutionContext.global) {
      def fail(response: String) = {
        println(response)
        Failure(new Exception("TODO"))
      }
      override def toAccount (response: String) = fail(response)
      override def toUser    (response: String) = fail(response)
      override def toToken   (response: String) = fail(response)
    }
    val result = Await.result(client.createUser(CreateUserRequest(6, "test2", None, "email", "type")), 10 seconds)
    println(result)
  }
}

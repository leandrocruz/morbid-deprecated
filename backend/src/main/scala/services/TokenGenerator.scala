package services

import domain.Token
import io.jsonwebtoken.{Claims, Jws}

import scala.concurrent.duration.Duration
import scala.util.Try

trait TokenGenerator {
  def createToken(issuer: String, subject: String, expiresIn: Option[Duration]): Token
  def verify(input: String): Try[Jws[Claims]]
}

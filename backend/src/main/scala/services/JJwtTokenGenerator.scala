package services

import java.nio.file.{Files, Paths}
import java.time.Clock
import java.util.{Base64, Date}

import domain.Token
import io.jsonwebtoken.{Jwts, SignatureAlgorithm}
import javax.crypto.spec.SecretKeySpec
import javax.inject.{Inject, Singleton}
import org.slf4j.LoggerFactory
import play.api.Configuration

import scala.concurrent.duration.Duration
import scala.util.Try

@Singleton
class JJwtTokenGenerator @Inject() (conf: Configuration, clock: Clock) extends TokenGenerator {

  val log = LoggerFactory.getLogger(getClass)

  val privateKey = conf.getOptional[String]("jwt.key") map {
    toString
  } map {
    toPrivateKey
  } getOrElse {
    throw new Exception("Can't read JWT key")
  }

  def toString(location: String): Array[Byte] = {
    log.info(s"Loading key from: '$location'")
    Files.readAllBytes(Paths.get(location))
  }

  def toPrivateKey(bytes: Array[Byte]) = {
    val decoded = Base64.getDecoder.decode(bytes)
    new SecretKeySpec(decoded, 0, decoded.length, "HmacSHA512")
  }

  override def createToken(issuer: String, subject: String, expiresIn: Option[Duration]): Token = {
    val now = clock.millis()
    val issuedAt = new Date(now)
    val expiresAt = expiresIn map { date => new Date(now + date.toMillis) }

    val jwt = Jwts
      .builder()
      .setSubject(subject)
      .setIssuedAt(issuedAt)
      .setIssuer(issuer)
      .signWith(SignatureAlgorithm.HS512, privateKey)

    expiresAt foreach { at =>  jwt.setExpiration(at) }

    Token(
      token = jwt.compact(),
      issuer = issuer,
      subject = subject,
      issuedAt = issuedAt,
      expiresAt = expiresAt
    )
  }

  override def verify(input: String) = Try {
    Jwts.parser().setSigningKey(privateKey).parseClaimsJws(input)
  }
}

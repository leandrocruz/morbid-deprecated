package store

import java.sql.Timestamp
import java.util.Date

import domain.collections.secrets
import domain._
import domain.utils._
import org.apache.commons.text.{CharacterPredicates, RandomStringGenerator}
import org.passay._
import org.slf4j.LoggerFactory
import services.{Services, TokenGenerator}
import slick.jdbc.PostgresProfile.api._

import scala.collection.JavaConverters._
import scala.concurrent.Future


object Passwords {

  /*
    at least one upper-case character
    at least one lower-case character
    at least one digit character
    at least one symbol (special character)
   */
  val genRules = Seq(
    new CharacterRule(EnglishCharacterData.UpperCase, 3),
    new CharacterRule(EnglishCharacterData.LowerCase, 3),
    new CharacterRule(EnglishCharacterData.Digit, 2))

  val validateRules = genRules ++ Seq(
    new LengthRule(8, 16),
    new CharacterRule(EnglishCharacterData.Special, 1),
    new WhitespaceRule)

  val generator = new PasswordGenerator
  val validator = new PasswordValidator(validateRules.asJava)

  def generate(len: Int) = generator.generatePassword(len, genRules.asJava)
  def validate(pwd: String) = validator.validate(new PasswordData(pwd)).isValid
}

trait Passwords extends ObjectStore[Password, CreatePasswordRequest] {
}

class DatabasePasswords (services: Services, db: Database, tokens: TokenGenerator) extends Passwords {

  implicit val ec = services.ec()

  val log = LoggerFactory.getLogger(getClass)

  val rnd = new RandomStringGenerator.Builder()
    .withinRange('0', 'z')
    .filteredBy(CharacterPredicates.DIGITS, CharacterPredicates.LETTERS)
    .build()

  override def byId(id: Long) : Future[Option[Password]] = Future.failed(new Exception("TODO"))

  override def create(request: CreatePasswordRequest) : Future[Password] = {
    val instant = services.clock().instant()
    val created = new Timestamp(instant.toEpochMilli)
    val token   = rnd.generate(32)
    val secret  = request.password.sha256()

    db.run {
      (secrets returning secrets.map(_.id)) += (
        0l,
        request.user,
        created,
        null,
        request.method,
        secret,
        token
      )
    } map { id =>
      Password(
        id        = id,
        user      = request.user,
        created   = Date.from(instant),
        deleted   = None,
        method    = request.method,
        password  = secret,
        token     = token
      )
    }

  }
}

package services

import org.passay._
import scala.collection.JavaConverters._

trait SecretValidator {
  def validate(pwd: String) : Boolean
  def generate(len: Int)    : String
}

class PassaySecretValidator extends SecretValidator {
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

  override def generate(len: Int) = generator.generatePassword(len, genRules.asJava)
  override def validate(pwd: String) = validator.validate(new PasswordData(pwd)).isValid
}

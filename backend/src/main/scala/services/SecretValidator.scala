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
    new CharacterRule(EnglishCharacterData.Digit    , 2),
  )

  val validateRules = Seq(
    new LengthRule(8, 16)                                       , // length between 8 and 16 characters
    new CharacterRule(EnglishCharacterData.UpperCase, 1)  , // at least one upper-case character
    new CharacterRule(EnglishCharacterData.LowerCase, 1)  , // at least one lower-case character
    new CharacterRule(EnglishCharacterData.Digit    , 1)  , // at least one digit character
    new CharacterRule(EnglishCharacterData.Special  , 1)  , // at least one symbol (special character)
    new WhitespaceRule()                                          // no whitespace
  )

  val generator = new PasswordGenerator
  val validator = new PasswordValidator(validateRules.asJava)

  override def generate(len: Int)     = generator.generatePassword(len, genRules.asJava)
  override def validate(pwd: String) = validator.validate(new PasswordData(pwd)).isValid
}

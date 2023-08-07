package services

import com.bastiaanjansen.otp._
import play.api.Configuration

import javax.inject.{Inject, Singleton}

object otp {

  trait OTPGenerator {
    def generate: OTPManager
  }

  @Singleton
  class OTPGeneratorImpl @Inject() (services : AppServices) extends OTPGenerator {

    val pwdLength = services.conf().getOptional[Int]("twoFactor.passwordLength").getOrElse(6)

    def generate: OTPManager = {
      val secret = SecretGenerator.generate(512)
      val hotp = new HOTPGenerator.Builder(secret)
        .withPasswordLength(pwdLength)
        .withAlgorithm(HMACAlgorithm.SHA256)
        .build()
      HOTPManagerImpl(hotp)
    }

  }

  trait OTPManager {
    def generateCode: String
    def verifyCode(code: String): Boolean
    def update: Unit
  }

  /*
   * See: https://blog.rockthejvm.com/otp-authentication-scala-http4s/#21-hmac-based-one-time-password-hotp
   */
  case class HOTPManagerImpl(generator: HOTPGenerator) extends OTPManager {

    var counter: Long = 0

    def generateCode: String = generator.generate(counter)

    def verifyCode(code: String): Boolean = {
      generator.verify(code, counter)
    }

    def update = counter += 1

  }

}

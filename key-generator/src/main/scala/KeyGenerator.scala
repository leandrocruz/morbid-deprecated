import io.jsonwebtoken.security.Keys
import io.jsonwebtoken._
import java.util.Base64

object KeyGenerator extends App {
    override def main(args: Array[String]) {
        val key     = Keys.secretKeyFor(SignatureAlgorithm.HS512)
        val encoded = key.getEncoded
        val payload = Base64.getEncoder.encodeToString(encoded)
        println(s"HMAC512 key is '$payload'")
    }
}
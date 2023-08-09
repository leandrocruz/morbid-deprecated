import io.jsonwebtoken.security.Keys
import io.jsonwebtoken._

import java.nio.file.Files
import java.util.Base64
import javax.crypto.spec.SecretKeySpec

object KeyGenerator extends App {
    override def main(args: Array[String]) {
        val key     = Keys.secretKeyFor(SignatureAlgorithm.HS512)
        val encoded = key.getEncoded
        val payload = Base64.getEncoder.encodeToString(encoded)
        val file = Files.createTempFile("secret-", ".key")
        Files.writeString(file, payload)
        
        println(s"HMAC512 key written to $file")
    }
}
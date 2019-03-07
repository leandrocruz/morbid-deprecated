package services

import javax.inject.Singleton
import org.apache.commons.text.{CharacterPredicates, RandomStringGenerator}

trait Random {
  def generate(len: Int): String
}

@Singleton
class SimpleRandom extends Random {

  val rnd = new RandomStringGenerator.Builder()
    .withinRange('0', 'z')
    .filteredBy(CharacterPredicates.DIGITS, CharacterPredicates.LETTERS)
    .build()

  override def generate(len: Int) = rnd.generate(len)
}

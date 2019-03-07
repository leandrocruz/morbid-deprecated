import java.time.Clock

import store._
import com.google.inject.AbstractModule
import services._

class Module extends AbstractModule {
  override def configure() = {
    bind(classOf[Clock]).toInstance(Clock.systemDefaultZone)
    bind(classOf[Random])          .to(classOf[SimpleRandom])          .asEagerSingleton()
    bind(classOf[SecretValidator]) .to(classOf[PassaySecretValidator]) .asEagerSingleton()
    bind(classOf[Services])        .to(classOf[BasicServices])         .asEagerSingleton()
    bind(classOf[RootActors])      .to(classOf[RootActorsImpl])        .asEagerSingleton()
    bind(classOf[TokenGenerator])  .to(classOf[JJwtTokenGenerator])
    bind(classOf[Stores])          .to(classOf[StoresImpl])
  }
}

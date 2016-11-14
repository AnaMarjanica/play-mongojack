package play.modules.mongojack

import com.google.inject.{Binder, Module, Provides}
import play.api.Application

class MongoDBModule(application: Application) extends Module{

  override def configure(binder: Binder): Unit = {

  }

  @Provides
  def provideWithMongoDBProvider() = {
    new MongoDBProvider(application)
  }

}

package play.modules.mongojack

import play.Plugin
import java.util.concurrent.ConcurrentHashMap

import play.api.Application
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.mongodb._
import org.mongojack.{JacksonDBCollection}
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.inject.Inject
import org.mongojack.internal.MongoJackModule

import scala.collection.JavaConversions._

class MongoDBProvider @Inject()(app: Application){

  private val cache = new ConcurrentHashMap[(String, Class[_], Class[_]), JacksonDBCollection[_, _]]()

  private lazy val (mongo, db, globalMapper, configurer) = {

    // Look up the object mapper configurer
    val configurer = app.configuration.getString("mongodb.objectMapperConfigurer") map {
      Class.forName(_).asSubclass(classOf[ObjectMapperConfigurer]).newInstance
    }

    // Configure the default object mapper
    val defaultMapper = MongoJackModule.configure(new ObjectMapper).registerModule(new DefaultScalaModule)

    val globalMapper = configurer map {
      _.configure(defaultMapper)
    } getOrElse defaultMapper

    val defaultWriteConcern = app.configuration.getString("mongodb.defaultWriteConcern") flatMap { value =>
      Option(WriteConcern.valueOf(value))
    }

    app.configuration.getString("mongodb.uri") match {
      case Some(uri) => {
        val mongoURI = new MongoClientURI(uri)
        val mongo = new MongoClient(mongoURI)
        val db = mongo.getDB(mongoURI.getDatabase)
        defaultWriteConcern.foreach { concern => db.setWriteConcern(concern) }
//        if (mongoURI.getUsername != null) {
//          if (!db.authenticate(mongoURI.getUsername, mongoURI.getPassword)) {
//            throw new IllegalArgumentException("MongoDB authentication failed for user: " + mongoURI.getUsername + " on database: "
//              + mongoURI.getDatabase);
//          }
//        }
        (mongo, db, globalMapper, configurer)
      }
      case None => {
        // Configure MongoDB
        // DB server string is comma separated, with optional port number after a colon
        val mongoDbServers = app.configuration.getString("mongodb.servers").getOrElse("localhost")
        // Parser for port number
        object Port {
          def unapply(s: String): Option[Int] = try {
            Some(s.toInt)
          } catch {
            case _: java.lang.NumberFormatException => None
          }
        }
        // Split servers
        val mongo = mongoDbServers.split(',') map {
          // Convert each server string to a ServerAddress, matching based on arguments
          _.split(':') match {
            case Array(host) => new ServerAddress(host)
            case Array(host, Port(port)) => new ServerAddress(host, port)
            case _ => throw new IllegalArgumentException("mongodb.servers must be a comma separated list of hostnames with" +
              " optional port numbers after a colon, eg 'host1.example.org:1111,host2.example.org'")
          }
        } match {
          case Array(single) => new MongoClient(single)
          case multiple => new MongoClient(multiple.toList)
        }

        // Load database
        val dbName = app.configuration.getString("mongodb.database").getOrElse("play")
        val db = mongo.getDB(dbName)

        // Write concern
        defaultWriteConcern.foreach { concern => db.setWriteConcern(concern) }

        // Authenticate if necessary
        val credentials = app.configuration.getString("mongodb.credentials")
//        credentials.foreach {
//          _.split(":", 2) match {
//            case Array(username: String, password: String) => {
//              if (!db.authenticate(username, password.toCharArray)) {
//                throw new IllegalArgumentException("MongoDB authentication failed for user: " + username + " on database: "
//                  + dbName);
//              }
//            }
//            case _ => throw new IllegalArgumentException("mongodb.credentials must be a username and password separated by a colon")
//          }
//        }

        (mongo, db, globalMapper, configurer)
      }
    }
  }

  def getCollection[T, K](name: String, entityType: Class[T], keyType: Class[K]): JacksonDBCollection[T, K] = {
    if (cache.containsKey((name, entityType, keyType))) {
      cache.get((name, entityType, keyType)).asInstanceOf[JacksonDBCollection[T, K]]
    } else {
      val mapper = configurer map {
        _.configure(globalMapper, name, entityType, keyType)
      } getOrElse globalMapper

      val mongoColl = db.getCollection(name)
      val coll = JacksonDBCollection.wrap(mongoColl, entityType, keyType, mapper)

      cache.putIfAbsent((name, entityType, keyType), coll)
      coll
    }
  }

  def client() = mongo

  def dispose(): Unit ={
    cache.clear()
    mongo.close()
  }

}

class MongoDBPlugin @Inject()(val app: Application) extends Plugin {

  val mongoDBProvider = new MongoDBProvider(app)

  def getCollection[T, K](name: String, entityType: Class[T], keyType: Class[K]): JacksonDBCollection[T, K] =
    mongoDBProvider.getCollection(name, entityType, keyType)

  override def onStart() {
    mongoDBProvider.client()
  }

  override def onStop() {
    // This config exists for testing, because when you close mongo, it closes all connections, and specs runs the
    // tests in parallel.
    if (!app.configuration.getString("mongodbJacksonMapperCloseOnStop").contains("disabled")) {
      mongoDBProvider.dispose()
    }
  }

  override def enabled() = !app.configuration.getString("mongodb.jackson.mapper").contains("disabled")
}

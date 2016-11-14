package play.modules.mongojack

import play.api.Application
import java.util.Locale
import java.lang.reflect.ParameterizedType
import org.mongojack.{MongoCollection, JacksonDBCollection}

/**
 * MongoDB Jackson Mapper module for play framework
 */
object MongoDB {
  private def error = throw new Exception(
    "MongoDBPlugin is not enabled"
  )

  /**
   * Get a collection.  This method takes an implicit application as a parameter, and so is the best option to use from
   * Scala, and can also be used while testing to pass in a fake application.
   *
   * @param name The name of the collection
   * @param entityType The type of the entity
   * @param keyType The type of the key
   */
  def collection[T, K](name: String, entityType: Class[T], keyType: Class[K])(implicit app: Application) : JacksonDBCollection[T, K] =
    app.plugin[MongoDBPlugin].map(_.getCollection(name, entityType, keyType)).getOrElse(error)

  /**
   * Get a collection.  Implicitly uses the camel case version of the class name, or the collection name configured by
   * a {@link net.vz.mongodb.jackson.MongoCollection} annotation if present.
   *
   * This method takes an implicit application as a parameter, and so is the best option to use from
   * Scala, and can also be used while testing to pass in a fake application.
   *
   * @param entityType The type of the entity
   * @param keyType The type of the key
   */
  def collection[T, K](entityType: Class[T], keyType: Class[K])(implicit app: Application) : JacksonDBCollection[T, K]= {
    val name = Option(entityType.getAnnotation(classOf[MongoCollection])).map(_.name).getOrElse {
      entityType.getSimpleName.substring(0, 1).toLowerCase(Locale.ENGLISH) + entityType.getSimpleName.substring(1)
    }
    collection(name, entityType, keyType)
  }

  /**
   * Get a collection.
   *
   * The passed in <code>entityType</code> must directly implement {@link play.modules.mongodb.jackson.KeyTyped} and specify the K
   * parameter, this is used as the key type.  If you don't want your objects implementing MongoDocument, simply
   * use the {@link MongoDB.collection(Class, Class)} method instead, and pass the keyType in there.
   *
   * This method takes an implicit application as a parameter, and so is the best option to use from
   * Scala, and can also be used while testing to pass in a fake application.
   *
   * @param name The name of the collection
   * @param entityType The type of the entity
   */
  def collection[T <: KeyTyped[K], K](name: String, entityType: Class[T with KeyTyped[K]])(implicit app: Application) : JacksonDBCollection[T, K] = {
    collection(name, entityType, determineKeyType(entityType))
  }

  /**
   * Get a collection.  Implicitly uses the camel case version of the class name, or the collection name configured by
   * a {@link net.vz.mongodb.jackson.MongoCollection} annotation if present.
   *
   * The passed in <code>entityType</code> must directly implement {@link play.modules.mongodb.jackson.KeyTyped} and specify the K
   * parameter, this is used as the key type.  If you don't want your objects implementing MongoDocument, simply
   * use the {@link MongoDB.collection(Class, Class)} method instead, and pass the keyType in there.
   *
   * This method takes an implicit application as a parameter, and so is the best option to use from
   * Scala, and can also be used while testing to pass in a fake application.
   *
   * @param entityType The type of the entity
   */
  def collection[T <: KeyTyped[K], K](entityType: Class[T with KeyTyped[K]])(implicit app: Application) : JacksonDBCollection[T, K] = {
    collection(entityType, determineKeyType(entityType))
  }

  /**
   * Get a collection.  This method uses the current application, and so will not work outside of the context of a
   * running app.
   *
   * @param name The name of the collection
   * @param entityType The type of the entity
   * @param keyType The type of the key
   */
  def getCollection[T, K](name: String, entityType: Class[T], keyType: Class[K]) = {
    // This makes simpler use from Java
    import play.api.Play.current
    collection(name, entityType, keyType)
  }

  /**
   * Get a collection.  Implicitly uses the camel case version of the class name, or the collection name configured by
   * a {@link net.vz.mongodb.jackson.MongoCollection} annotation if present.
   *
   * This method uses the current application, and so will not work outside of the context of a running app.
   *
   * @param entityType The type of the entity
   * @param keyType The type of the key
   */
  def getCollection[T, K](entityType: Class[T], keyType: Class[K]) = {
    // This makes simpler use from Java
    import play.api.Play.current
    collection(entityType, keyType)
  }

  /**
   * Get a collection.
   *
   * The passed in <code>entityType</code> must directly implement {@link play.modules.mongodb.jackson.KeyTyped} and specify the K
   * parameter, this is used as the key type.  If you don't want your objects implementing MongoDocument, simply
   * use the {@link MongoDB.getCollection(Class, Class)} method instead, and pass the keyType in there.
   * This method uses the current application, and so will not work outside of the context of a running app.
   *
   * @param name The name of the collection
   * @param entityType The type of the entity
   */
  def getCollection[T <: KeyTyped[K], K](name: String, entityType: Class[T with KeyTyped[K]]) : JacksonDBCollection[T, K] = {
    // This makes simpler use from Java
    import play.api.Play.current
    collection(name, entityType)
  }

  /**
   * Get a collection.  Implicitly uses the camel case version of the class name, or the collection name configured by
   * a {@link net.vz.mongodb.jackson.MongoCollection} annotation if present.
   *
   * The passed in <code>entityType</code> must directly implement {@link play.modules.mongodb.jackson.KeyTyped} and specify the K
   * parameter, this is used as the key type.  If you don't want your objects implementing MongoDocument, simply
   * use the {@link MongoDB.getCollection(Class, Class)} method instead, and pass the keyType in there.

   * This method uses the current application, and so will not work outside of the context of a running app.
   *
   * @param entityType The type of the entity
   */
  def getCollection[T <: KeyTyped[K], K](entityType: Class[T with KeyTyped[K]]) : JacksonDBCollection[T, K] = {
    // This makes simpler use from Java
    import play.api.Play.current
    collection(entityType)
  }

  private def determineKeyType[K](entityType: Class[_ <: KeyTyped[K]]) : Class[K] = {
    entityType.getGenericInterfaces flatMap {
      case p: ParameterizedType =>  Array(p)
      case _ => Nil
    } find {
      _.getRawType == classOf[KeyTyped[_]]
    } map {
      case p: ParameterizedType => p
    } map {_.getActualTypeArguments()(0)} map {
      case c: Class[K] => c
    } getOrElse {
      throw new IllegalArgumentException("MongoDocument type parameter not declared on passed in entity type")
    }
  }
}



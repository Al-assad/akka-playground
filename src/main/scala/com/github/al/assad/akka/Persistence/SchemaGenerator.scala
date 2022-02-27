package com.github.al.assad.akka.Persistence

import com.github.al.assad.akka.Persistence.Schema.SchemaType
import com.typesafe.config.Config
import slick.jdbc.JdbcBackend.Database

import scala.io.Source
import scala.language.postfixOps
import scala.util.{Failure, Success, Using}


/**
 * akka persistence jdbc backend type
 */
object Schema {
  sealed abstract class SchemaType(val schema: String, val drop: String)

  case object H2 extends SchemaType(
    schema = "akka-persistence-schema/h2-create.sql",
    drop = "akka-persistence-schema/h2-drop.sql")

  case object Postgres extends SchemaType(
    schema = "akka-persistence-schema/postgres-create.sql",
    drop = "akka-persistence-schema/postgres-drop.sql")
}

/**
 * akka persistence ddl schema auto generator
 */
object SchemaGenerator {

  case class DatabaseConfig(url: String, driver: String, user: String, password: String)

  def extractDatabaseConfig(config: Config): DatabaseConfig = {
    val slickDbConf = config.getConfig("slick.db")
    DatabaseConfig(
      url = slickDbConf.getString("url"),
      driver = slickDbConf.getString("driver"),
      user = slickDbConf.getString("user"),
      password = slickDbConf.getString("password")
    )
  }

  def inferSchemaType(config: Config): SchemaType = config.getString("slick.profile") match {
    case p if p.contains("H2Profile") => Schema.H2
    case p if p.contains("PostgresProfile") => Schema.Postgres
    case _ => throw new IllegalArgumentException("unknown profile")
  }

  private def runScriptFromFile(config: Config, scriptPath: SchemaType => String): Unit = {
    val schemaType = inferSchemaType(config)
    val dbConfig = extractDatabaseConfig(config)
    val db: Database = Database.forURL(url = dbConfig.url, driver = dbConfig.driver, user = dbConfig.user, password = dbConfig.password)
    Using(db) { db =>
      Using(db.createSession()) { session =>
        val schema = Source.fromResource(scriptPath(schemaType)).mkString
        session.withStatement() { statement =>
          statement.execute(schema)
        }
      } match {
        case Success(_) => println("akka persistence schema created.")
        case Failure(ex) => ex.printStackTrace()
      }
    }
  }

  // create akka-persistence schema
  def createSchema(config: Config): Unit = runScriptFromFile(config, _.schema)

  // drop akka-persistence schema
  def dropSchema(config: Config): Unit = runScriptFromFile(config, _.drop)
}

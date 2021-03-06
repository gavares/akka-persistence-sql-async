package akka.persistence.helper

import akka.persistence.PluginSpec
import akka.persistence.common.{SQLAsyncConfig, ScalikeJDBCExtension}
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scalikejdbc.SQL
import scalikejdbc.async._

trait DatabaseInitializer extends PluginSpec {
  protected lazy val sqlAsyncConfig: SQLAsyncConfig = ScalikeJDBCExtension(system).config
  private[this] def executeDDL[A](ddl: Seq[String]): Unit = {
    implicit val executor = sqlAsyncConfig.system.dispatcher
    val provider = ScalikeJDBCExtension(sqlAsyncConfig.system).sessionProvider
    val result = provider.localTx { implicit session =>
      ddl.foldLeft(Future.successful(())) { (result, d) =>
        result.flatMap { _ =>
          SQL(d).update().future().map(_ => ())
        }
      }
    }
    Await.result(result, 10.seconds)
  }
  protected def createJournalTableDDL: String
  private[this] def dropJournalTableDDL: String = {
    s"DROP TABLE IF EXISTS ${sqlAsyncConfig.journalTableName}"
  }
  protected def createSnapshotTableDDL: String
  private[this] def dropSnapshotTableDDL: String = {
    s"DROP TABLE IF EXISTS ${sqlAsyncConfig.snapshotTableName}"
  }

  /**
   * Flush DB.
   */
  protected override def beforeAll(): Unit = {
    val ddl = Seq(dropJournalTableDDL, dropSnapshotTableDDL, createJournalTableDDL, createSnapshotTableDDL)
    executeDDL(ddl)
    super.beforeAll()
  }
}

trait MySQLInitializer extends DatabaseInitializer {
  override protected def createJournalTableDDL: String = {
    s"""
        |CREATE TABLE IF NOT EXISTS ${sqlAsyncConfig.journalTableName} (
        |  persistence_id VARCHAR(255) NOT NULL,
        |  sequence_nr BIGINT NOT NULL,
        |  message BLOB NOT NULL,
        |  PRIMARY KEY (persistence_id, sequence_nr)
        |)
      """.stripMargin
  }

  override protected def createSnapshotTableDDL: String = {
    s"""
        |CREATE TABLE IF NOT EXISTS ${sqlAsyncConfig.snapshotTableName} (
        |  persistence_id VARCHAR(255) NOT NULL,
        |  sequence_nr BIGINT NOT NULL,
        |  created_at BIGINT NOT NULL,
        |  snapshot BLOB NOT NULL,
        |  PRIMARY KEY (persistence_id, sequence_nr)
        |)
     """.stripMargin
  }
}

trait PostgreSQLInitializer extends DatabaseInitializer {
  override protected def createJournalTableDDL: String = {
    s"""
        |CREATE TABLE IF NOT EXISTS ${sqlAsyncConfig.journalTableName} (
        |  persistence_id VARCHAR(255) NOT NULL,
        |  sequence_nr BIGINT NOT NULL,
        |  message BYTEA NOT NULL,
        |  PRIMARY KEY (persistence_id, sequence_nr)
        |)
      """.stripMargin
  }

  override protected def createSnapshotTableDDL: String = {
    s"""
        |CREATE TABLE IF NOT EXISTS ${sqlAsyncConfig.snapshotTableName} (
        |  persistence_id VARCHAR(255) NOT NULL,
        |  sequence_nr BIGINT NOT NULL,
        |  created_at BIGINT NOT NULL,
        |  snapshot BYTEA NOT NULL,
        |  PRIMARY KEY (persistence_id, sequence_nr)
        |)
     """.stripMargin
  }
}

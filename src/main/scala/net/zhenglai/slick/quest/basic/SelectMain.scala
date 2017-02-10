package net.zhenglai.slick.quest.basic

import scala.concurrent.Await
import scala.concurrent.duration._

import slick.driver.H2Driver.api._

object SelectMain extends App {

  // Case class representing a row in table
  final case class Message(
    sender: String,
    content: String,
    id: Long = 0L
  )

  // helper method for creating test data
  def freshTestData = Seq(
    Message("Dave", "Hello, HAL. Do you read me, HAL?"),
    Message("HAL", "Affirmative, Dave. I read you."),
    Message("Dave", "Open the pod bay doors, HAL."),
    Message("HAL", "I'm sorry, Dave. I'm afraid I can't do that.")
  )

  // Schema for the "message" table
  // tag => table alias
  // TODO: Tag??
  final class MessageTable(tag: Tag)
    extends Table[Message](tag, "message") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)

    def sender = column[String]("sender")

    def content = column[String]("content")

    def * = (sender, content, id) <> (Message.tupled, Message.unapply)
  }

  // Base query for querying the message table:
  // select * style query ...
  lazy val messages = TableQuery[MessageTable]
  println(s"{messages.shaped.shape} = ${messages.shaped.shape}")
  println(s"messages.shaped.value = ${messages.shaped.value}")

  val halSays = messages.filter(_.sender === "HAL")

  // create an in-memory H2 db
  // a factory for managing connections and transactions
  val db = Database.forConfig("quest")
  // slick manage connections & transactions with auto-commit

  // helper method for running a query
  def exec[T](program: DBIO[T]): T = Await.result(db.run(program), 2 seconds)

  println("Creating `messages` table:")
  println(s"messages.schema.createStatements.mkString = ${messages.schema.createStatements.mkString}")
  // type DBIO[+R] = DBIOAction[R, NoStream, Effect.All]
  val createAction: DBIO[Unit] = messages.schema.create
  exec(createAction) // send action to db

  println(s"\nInserting test data")
  // ++= bulk/batch insert
  // return is optional since some db doesn't guarantee row counts returned
  val insert: DBIO[Option[Int]] = messages ++= freshTestData
  println("rows inserted: " + exec(insert))

  println(s"\nSelecting all messages:")
  exec(messages.result) foreach println

  println(s"\nSelecting only messages from HAL:")
  println("selecting sql: " + halSays.result.statements.mkString)
  exec(halSays.result) foreach println

  println(s"\nSelecting ids with messages from HAL:")
  exec(halSays.map(_.id)) foreach println
}

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
  final class MessageTable(tag: Tag)
    extends Table[Message](tag, "message") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)

    def sender = column[String]("sender")

    def content = column[String]("content")

    def * = (sender, content, id) <> (Message.tupled, Message.unapply)
  }

  // Base query for querying the message table:

  lazy val messages = TableQuery[MessageTable]
  println(s"{messages.shaped.shape} = ${messages.shaped.shape}")
  println(s"messages.shaped.value = ${messages.shaped.value}")

  val halSays = messages.filter(_.sender === "HAL")

  // create an in-memory H2 db
  val db = Database.forConfig("quest")

  // helper method for running a query
  def exec[T](program: DBIO[T]): T = Await.result(db.run(program), 2 seconds)

  println("Creating `messages` table:")
  exec(messages.schema.create)

  println(s"\nInserting test data")
  exec(messages ++= freshTestData)

  println(s"\nSelecting all messages:")
  exec(messages.result) foreach println

  println(s"\nSeleecting only messages from HAL:")
  exec(halSays.result) foreach println
}

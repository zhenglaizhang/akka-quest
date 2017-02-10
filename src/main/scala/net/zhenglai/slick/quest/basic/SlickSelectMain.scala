package net.zhenglai.slick.quest.basic

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import slick.dbio.Effect.Read
import slick.driver.H2Driver.api._
import slick.profile.{ FixedSqlAction, FixedSqlStreamingAction }


object SlickSelectMain extends App {

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
  // todo: why lazy??
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

//  demoBySingleStep

  private def demoBySingleStep = {
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

    val wow: Query[Rep[String], String, Seq] = halSays.map(_.content).filter { content: Rep[String] =>
      content.like("%new%")
    }


    println(s"\nSelecting ids with messages from HAL:")
    // TODO: fix it
//    exec(halSays.map(_.id).filter { id: Rep[Long] => id < 10 }.result) foreach println

    // Query is monad, implements map, flatMap, filter and withFilter
    val halSay2 = for {
      message <- messages if message.sender === "HAL" // WHERE
    } yield (message.id, message.sender)

    // execute the query against db
    println(s"halSay2.result.statements.mkString = ${halSay2.result.statements.mkString}")
    // select "id", "sender" from "message" where "sender" = 'HAL'
    exec(halSay2.result)
  }

  // Like query, DBIOAction is also a monad

  // compose queries and actions is to wrap them inside a transaction
  val actions: DBIO[Seq[Message]] =
    messages.schema.create >>
      (messages ++= freshTestData) >>
      halSays.result

  println(s"result: " + exec(actions))

  // SELECT 1 FROM any_existing_table WHERE 1=0
  println("constant queries: " + Query(1).result.statements.mkString)
  println("select 1 result: " + exec(Query(1).result))

  println(exec(messages += Message("Dave", "I am new message")))
  println(exec(messages.filter { messageTable: MessageTable =>
    messageTable.sender === "Dave"
  }.result))


  println("result: " + messages.map(m => (m.id, m.sender)).result.statements)

  case class TextOnly(id: Long, content: String)

  val contentQuery = messages.
    map(m => (m.id, m.content) <> (TextOnly.tupled, TextOnly.unapply))
  println(contentQuery.result.statements.mkString)
  println(s"exec(contentQuery) = ${exec(contentQuery.result)}")

  println(messages.map(_.id * 1000L).result.statements)

  val containsBay = for {
    m <- messages
    if m.content like "%bay%"
  } yield m
  val bayMentioned: FixedSqlAction[Boolean, NoStream, Read] = containsBay.exists.result
  /**
    * select exists(
    * select "sender", "content", "id"
    * from "message"
    * where "content" like '%bay%'
    * )
    */
  println(bayMentioned.statements.mkString)

  Future {
    println("1")
    Thread.sleep(3000)
    3
  } map {
    println("2")
    println(_)
  }

  val woww: FixedSqlStreamingAction[Seq[Message], Message, Read] = messages.result


  // executing actions
  val mat = db.run(halSays.result)
  // materialized result
  val stream = db.stream(halSays.result) // streaming, handling large data set incrementally
  stream.foreach(println)
//  println("" + exec(db.stream(messages).result))

  // Rep comparision methods: operand types: A or Option[A]
  // ===
  // =!=
  // <
  // >
  // <=
  // >=
  messages.filter(_.sender === "Dave").result.statements
  messages.filter(_.sender =!= "Dave").result // sql equivalent operator: <>
  messages.filter(_.sender < "HAL")
  messages.filter(m => m.sender >= m.content).result.statements





  // ++ method for string concatenation (SQL's || operator):
  val concat = messages.map(m => m.sender ++ "> " ++ m.content).result
  println(concat.statements.mkString)
  println(exec(concat))

  Thread.sleep(1000)
}

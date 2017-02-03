package net.zhenglai.akka.quest.stream

import java.nio.file.Paths

import scala.concurrent.{ Await, Future }

import akka.{ Done, NotUsed }
import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.scaladsl.{ FileIO, Flow, Keep, Sink, Source }
import akka.stream.{ ActorMaterializer, IOResult, ThrottleMode }
import akka.util.ByteString
import scala.concurrent.duration._

object SimpleStreamMain extends App {
  // NotUsed: auxiliary value
  implicit val system: ActorSystem = ActorSystem("SimpleStreamSystem")
  implicit val mat: ActorMaterializer = ActorMaterializer()
  implicit val ec = system.dispatcher
  val log = Logging(system, getClass)

  val source: Source[Int, NotUsed] = Source(1 to 100)

  // The Materializer is a factory for stream execution engines, it is the thing that makes streams run

  // the Source is just a description of what you want to run, and like an architect’s blueprint it can be reused, incorporated into a larger design.
  //  source.runFold(0)(_ + _).foreach(i => print(s"sum: $i"))
  //  source.runForeach(i => log.info(i.toString))


  def lineSink(fileName: String): Sink[String, Future[IOResult]] =
    Flow[String] // left: NotUsed
      .map(s => ByteString(s + "\n"))
      .toMat(FileIO.toPath(Paths.get(fileName)))(Keep.right)

  val factorials = source.scan(BigInt(1))((acc, next) => acc * next)
  val result: Future[IOResult] =
    factorials
      .map(num => ByteString(s"$num\n"))
      .runWith(FileIO.toPath(Paths.get("target/factorials.txt")))
  result.foreach(r => log.info("io result: {}", r))

  factorials
    .map(_.toString)
    .runWith(lineSink("target/factorials2.txt"))

  // Akka Streams implicitly implement pervasive flow control, all combinators respect back-pressure.
  // This allows the throttle combinator to signal to all its upstream sources of data that it can only accept elements at a certain rate
  // —when the incoming rate is higher than one per second the throttle combinator will assert back-pressure upstream.
  val done: Future[Done] =
    factorials
      //      .zipWith(Source(0 to 100))((num, idx) => s"$idx! = $num")
      .zipWithIndex
      .map(s => s"${s._2}! = ${s._1}")
      .throttle(1, 100 milliseconds, 1, ThrottleMode.shaping)
      .runForeach(println)


  Await.ready(result, 10.seconds)
  print(Await.result(done, 100.seconds))
  mat.shutdown()
  system.terminate()
}